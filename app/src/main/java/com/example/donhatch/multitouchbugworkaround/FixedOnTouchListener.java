// The infamous Oreo 8.1 multitouch bug
//
// https://android-review.googlesource.com/c/platform/frameworks/native/+/640606/
//
//
// TODO: reconcile annotations with current algorithm (they are showing the old algorithm from before I made it more flexible)
// TODO: be able to actually play back the stuff parsed from a dump file:
//   - be able to convert from LogicalMotionEvent(s) to MotionEvent
// TODO: dump to a file instead of logcat, so it's not subject to size and rate constraints and random droppage

// BUG: See BAD00-- still not fixed
// BUG: See BAD01-- still not fixed
// BUG: Known ways to mess it up:
//      1.
//          - 0 down
//          - 1 down
//          - 0 up
//          - with 1 moving: 0&2 down simultanously and move.
//          - if it doesn't work, 0&2 up and repeat previous step.
//      2.
//          - 0 down
//          - 1 down
//          - 2 down
//          - with 2 moving: 0&1 down simultaneously and move.
//          - if it doesn't work, 0&1 up and repeat previous step.
//
// BUG: apparently the following gets snargled up:  the condition isn't quite right when there are two POINTER_DOWNs at the same time.
/*
0.803  253/530:                  {1}                                                                1: 1750.39221,1135.21167,0.850000024,0.214355469
0.812  254/530:                  {1}                                                                1: 1734.39783,1144.20544,0.824999988,0.215332031 A
0.817  255/530:           MOVE   {1}                                                                1: 1726.40063,1148.70239,0.824999988,0.215332031
0.822  256/530:  POINTER_DOWN(0) {0,1}          0: 2221.22876,500.652313,0.587500036,0.176757813 A!  1: 1734.39783,1144.20544,0.824999988,0.215332031 A
0.822  257/530:  POINTER_DOWN(2) {0,1,2}        0:[2221.22876,500.652313,0.587500036,0.176757813]A   1:[1734.39783,1144.20544,0.824999988,0.215332031]A   2: 676.765015,578.598206,0.775000036,0.179199219
0.822  258/530:                  {0,1,2}        0:[2221.22876,500.652313,0.587500036,0.176757813]A   1: 1719.40308,1152.19983,0.800000012,0.214355469 B   2:[676.765015,578.598206,0.775000036,0.179199219]
0.828  259/530:           MOVE   {0,1,2}        0:[2221.22876,500.652313,0.587500036,0.176757813]A   1:[1719.40308,1152.19983,0.800000012,0.214355469]B   2:[676.765015,578.598206,0.775000036,0.179199219]
0.832  260/530:                  {0,1,2}        0:(2221.22876,500.652313,0.612500012,0.178710938)A   1:[1719.40308,1152.19983,0.800000012,0.214355469]B   2:[676.765015,578.598206,0.775000036,0.179199219]
0.832  261/530:                  {0,1,2}        0:[2221.22876,500.652313,0.612500012,0.178710938]A   1:[1719.40308,1152.19983,0.800000012,0.214355469]B   2:(676.765015,578.598206,0.800000012,0.185058594)
0.832  262/530:           MOVE   {0,1,2}        0:[2221.22876,500.652313,0.612500012,0.178710938]A   1: 1711.40576,1158.19568,0.787500024,0.215332031     2:[676.765015,578.598206,0.800000012,0.185058594]
0.842  263/530:                  {0,1,2}        0:(2221.22876,500.652313,0.637499988,0.178710938)A   1: 1719.40308,1152.19983,0.787500024,0.215332031 B   2:[676.765015,578.598206,0.800000012,0.185058594]
0.842  264/530:                  {0,1,2}        0:[2221.22876,500.652313,0.637499988,0.178710938]A   1:[1719.40308,1152.19983,0.787500024,0.215332031]B   2:(676.765015,578.598206,0.812500000,0.188476563)
*/
//
// Observation:
//      - when one pointer is down, we never get fully-duplicate MOVE events (i.e. equal on all axes to previous).
//              (oh wait! we *do* get it if it's id=1... not when it's id=0 though)
//              (woops not true-- we get it on id=0 too.  ok this is useless)
//        when multiple pointers down, we sometimes do... but *something* has always changed in some axis of some pointer id.

// Q: what is the first evidence that it's happening?
//    - sometimes the non-0 pointer begins bugging before the 0 pointer even moves
//      from its down position, so we have to recognize it-- how?
//    - seems to always start with the non-0 repeating the thing it's going to get stuck on,
//      twice or more, while 0-id is still in its down position.
// Q: what other changes are happening that I'm not tracking, and can I use them to characterize?
//    - pressure? size? axis? blah blah blah
//    - AXIS_HAT_X? AXIS_RELATIVE_X?
// Q: can the bug affect a *UP? or just moves?
// CONJECTURES:
//  - when it begins, it is always the case that:
//      - id 0 just went down for the not-first time
//      - exactly one other id is already down (can be 1, or 2, or...)
//      - the other id may have participated in the bug already, it doesn't matter
//  - it happens to the two participating ids, for almost exactly as long as both down;
//    as soon as one of them goes up: it stops happening to 0 (of course),
//    and the current run of the other is the last one where the bug occurs.
//  - so the anchor of id 0 is the down position!  And the anchor of the other one
//    is its value at the moment of that 0-down.  (FALSE-- not at the exact moment;
//    it might take another event or more for other id to settle into the bug)
//  - the anchor of the other has *not* appeared yet at the moment of 0-down.
//  - it's its *next* distinct position (after possible repeats of current) (FALSE-- can be 2 after, maybe even more, I'm not sure)
//  - the first occurrence of the bad non-0-id value (which doesn't need to be corrected there) is at the end of the first MOVE packet after the POINTER_DOWN(0); and that packet always contains one additional history not equal to the stuck value. (what if that pointer isn't moving, you may ask?  well in that case the bug doesn't happen!)
//      Wait that's false-- that one historical can have the same value.
//  - smoking gun: if size,pressure are same as previous but x,y are *not* same as previous, then x,y very likely *should* be same as previous (corrected)
//    on the other hand, if size,pressure are *not* same as previous (corrected), then this particular x,y isn't being affected by the bug.
/*
3.614  805/997:                  {1}                             1: 2492.13,711.506,1.50000,0.323242
3.617  806/997:           MOVE   {1}                             1: 2490.36,711.062,1.50000,0.323242
3.622  807/997:                  {1}                             1: 2488.14,710.507,1.50000,0.320801 D
3.626  808/997:           MOVE   {1}                             1: 2486.14,710.007,1.50000,0.320801
3.631  809/997:  POINTER_DOWN(0) {0, 1}     0: 2363.18,1045.27,0.587500,0.159180 A  1: 2488.14,710.507,1.50000,0.320801 D
3.631  810/997:                  {0, 1}     0:(2363.18,1045.27,0.587500,0.159180)A  1: 2483.14,708.508,1.50000,0.317383 E
3.634  811/997:           MOVE   {0, 1}     0:(2363.18,1045.27,0.587500,0.159180)A  1:(2483.14,708.508,1.50000,0.317383)E
3.639  812/997:                  {0, 1}     0:(2363.18,1045.27,0.600000,0.162598)A  1:(2483.14,708.508,1.50000,0.317383)E
3.639  813/997:           MOVE   {0, 1}     0:(2363.18,1045.27,0.600000,0.162598)A  1: 2479.14,706.509,1.48750,0.314941
3.648  814/997:                  {0, 1}     0:(2363.18,1045.27,0.612500,0.166504)A  1: 2483.14,708.508,1.48750,0.314941 E
*/
//    ARGH! here is a counterexample:  size,pressure not same as previous,  but x,y *should* be corrected to previous corrected.
/*
1.567  338/368:           MOVE   {0, 1}  0: 811.718201,661.540588,1.11250007,0.239746094,-1.57079637 A?  1: 1434.50195,491.658569,1.20000005,0.250976563,-1.57079637
1.575  339/368:           MOVE   {0, 1}  0: 832.710876,1000.30536,1.11250007,0.238769531,-1.57079637     1: 1474.48804,435.697418,1.20000005,0.250976563,-1.57079637 B?
1.584  340/368:                  {0, 1}  0: 833.710510,1000.30536,1.11250007,0.238769531,-1.57079637     1:(1474.48804,435.697418,1.20000005,0.250976563,-1.57079637)B
1.584  341/368:           MOVE   {0, 1}  0: 811.718201,661.540588,1.11250007,0.238769531,-1.57079637 A?  1: 1434.50195,492.657867,1.20000005,0.250976563,-1.57079637
1.592  342/368:                  {0, 1}  0: 834.710205,1000.30536,1.11250007,0.238769531,-1.57079637     1: 1474.48804,435.697418,1.20000005,0.250976563,-1.57079637 B?
1.592  343/368:           MOVE   {0, 1}  0: 811.718201,661.540588,1.11250007,0.238769531,-1.57079637 A?  1: 1434.50195,493.657166,1.20000005,0.250976563,-1.57079637
1.601  344/368:           MOVE   {0, 1}  0: 835.709839,1001.30463,1.11250007,0.238769531,-1.57079637     1: 1474.48804,435.697418,1.20000005,0.250976563,-1.57079637 B?
1.609  345/368:           MOVE   {0, 1}  0: 811.718201,661.540588,1.11250007,0.238769531,-1.57079637 A?  1: 1434.50195,494.656494,1.20000005,0.251953125,-1.57079637
1.617  346/368:           MOVE   {0, 1}  0:(811.718201,661.540588,1.11250007,0.237792969,-1.57079637)A   1: 1474.48804,435.697418,1.20000005,0.251953125,-1.57079637 B?
1.634  347/368:           MOVE   {0, 1}  0: 836.709473,1001.30463,1.11250007,0.237792969,-1.57079637     1:(1474.48804,435.697418,1.20000005,0.251953125,-1.57079637)B
1.642  348/368:                  {0, 1}  0: 811.718201,661.540588,1.11250007,0.237304688,-1.57079637 A   1:(1474.48804,435.697418,1.20000005,0.251953125,-1.57079637)B
1.642  349/368:           MOVE   {0, 1}  0:(811.718201,661.540588,1.11250007,0.237304688,-1.57079637)A   1: 1434.50195,495.655792,1.20000005,0.251953125,-1.57079637
1.667  350/368:           MOVE   {0, 1}  0:(811.718201,661.540588,1.10000002,0.237304688,-1.57079637)A   1: 1474.48804,435.697418,1.20000005,0.251953125,-1.57079637 B?
1.676  351/368:                  {0, 1}  0:(811.718201,661.540588,1.10000002,0.237304688,-1.57079637)A   1:(1474.48804,435.697418,1.20000005,0.251953125,-1.57079637)B
1.676  352/368:           MOVE   {0, 1}  0:(811.718201,661.540588,1.10000002,0.237304688,-1.57079637)A   1:(1474.48804,435.697418,1.20000005,0.251953125,-1.57079637)B
1.684  353/368:           MOVE   {0, 1}  0:(811.718201,661.540588,1.10000002,0.237304688,-1.57079637)A   1: 1435.50159,495.655792,1.20000005,0.251953125,-1.57079637
1.701  354/368:           MOVE   {0, 1}  0: 837.709167,1001.30463,1.10000002,0.236328125,-1.57079637     1: 1474.48804,435.697418,1.20000005,0.251953125,-1.57079637 B?
1.709  355/368:           MOVE   {0, 1}  0: 838.708801,1001.30463,1.10000002,0.236328125,-1.57079637     1:(1474.48804,435.697418,1.20000005,0.251953125,-1.57079637)B
1.718  356/368:           MOVE   {0, 1}  0: 840.708130,1001.30463,1.10000002,0.235351563,-1.57079637     1:(1474.48804,435.697418,1.20000005,0.251953125,-1.57079637)B
1.726  357/368:           MOVE   {0, 1}  0: 842.707397,1001.30463,1.08749998,0.234375000,-1.57079637     1:(1474.48804,435.697418,1.20000005,0.251953125,-1.57079637)B
*/
//  - every time it gives a bad (stuck) x,y, the *correct* value is the *previous* x,y.
//    the current size,pressure are always (correctly) the same as th size,pressure
//    from that previous one.
// Ooh important-- the bug can persist on the 0 id for a short time after one of the participating ids went UP!  In fact it lasted for 2 logical events!  Seems there's little we can say
/*
8.973 1514/1552:                  {0, 1}  0:(1614.43945,884.385803,1.48750007,0.250976563,-1.57079637)A   1:(2393.16919,686.523193,1.28750002,0.237792969,-1.57079637)A
8.973 1515/1552:           MOVE   {0, 1}  0:(1614.43945,884.385803,1.48750007,0.250976563,-1.57079637)A   1:(2393.16919,686.523193,1.23750007,0.238769531,-1.57079637)A
8.981 1516/1552:           MOVE   {0, 1}  0:(1614.43945,884.385803,1.48750007,0.251953125,-1.57079637)A   1:(2393.16919,686.523193,1.23750007,0.238769531,-1.57079637)A
8.981 1517/1552:    POINTER_UP(1) {0, 1}  0:(1614.43945,884.385803,1.48750007,0.251953125,-1.57079637)A   1:(2393.16919,686.523193,1.23750007,0.238769531,-1.57079637)A
8.988 1518/1552:           MOVE   {0}     0:(1614.43945,884.385803,1.50000000,0.250488281,-1.57079637)A
9.005 1519/1552:           MOVE   {0}     0:(1614.43945,884.385803,1.50000000,0.250488281,-1.57079637)A
9.014 1520/1552:                  {0}     0: 469.836884,1031.28381,1.50000000,0.250488281,-1.57079637
*/
// - also seen the non-0 be bad on the 0-UP... and once in  while, it's even bad a bit after the 0-up
/*
2.574  519/574:                  {0, 1}  0: 314.890656,1050.27063,1.18750000,0.231445313,-1.57079637     1:(2020.29858,540.124939,1.47500002,0.258789063,-1.57079637)A
2.583  520/574:           MOVE   {0, 1}  0: 313.891022,1050.27063,1.14999998,0.231445313,-1.57079637     1:(2020.29858,540.124939,1.47500002,0.258789063,-1.57079637)A
2.591  521/574:    POINTER_UP(0) {0, 1}  0:(313.891022,1050.27063,1.14999998,0.231445313,-1.57079637)    1:(2020.29858,540.124939,1.47500002,0.258789063,-1.57079637)A
2.623  522/574:           MOVE   {1}                          1:(2020.29858,540.124939,1.47500002,0.258789063,-1.57079637)A
2.640  523/574:                  {1}                          1: 2193.23853,510.645386,1.47500002,0.258789063,-1.57079637
2.648  524/574:           MOVE   {1}                          1: 2193.23853,510.177124,1.47500002,0.258789063,-1.57079637
*/
//  - first appearance of wrongness is always in idNonzero, always during the initial static x,y of id0 on its POINTER_DOWN  (which always seems to be about 40 to 60 ms, regardless of whether bugging)
//  - that first bad x,y, which is in idNonzero as previously stated, is always 16 to 17 ms after id0's POINTER_DOWN  (or, occasionally, 25ms)
//  - when bugging, there are no dups other than the wrong x,y's  (because dups *turn into* the wrong x,y's)
//    therefore if we see a dup other than the anchor, the bug is NOT happening.
//  - when bugging, wrong x,y values always occur when it would otherwise be a dup-- which, I think, occur when some *other* id changed?  (or, maybe, same id pressure changed)
//  - when the bug happens, idNonzero's x,y always:
//      - starts at anchor, maybe stays there a while with same pressure&size, goes to 1 other value (same or diff p&s), immediately comes back to anchor with same p&s as prev.
// WRONG. counterexample:
/*
1.245  242/354:           MOVE   {1}                                                         1: 2491.13501,805.440674,1.57500005,0.319824219
1.254  243/354:  POINTER_DOWN(0) {0, 1}  0: 2131.26001,1080.24976,0.937500000,0.218261719 A!  1:(2491.13501,805.440674,1.57500005,0.319824219)
1.254  244/354:                  {0, 1}  0:(2131.26001,1080.24976,0.937500000,0.218261719)A   1: 2490.13550,807.439270,1.56250000,0.318359375
1.258  245/354:           MOVE   {0, 1}  0:(2131.26001,1080.24976,0.937500000,0.218261719)A   1: 2489.63574,808.438599,1.56250000,0.318359375 A!
1.262  246/354:                  {0, 1}  0:(2131.26001,1080.24976,0.937500000,0.219238281)A   1:(2489.63574,808.438599,1.56250000,0.318359375)A?
1.262  247/354:                  {0, 1}  0:(2131.26001,1080.24976,0.937500000,0.219238281)A   1: 2490.13550,808.438599,1.55000007,0.317382813
1.271  248/354:           MOVE   {0, 1}  0:(2131.26001,1080.24976,0.937500000,0.219238281)A   1: 2489.13574,810.437195,1.53750002,0.315917969
1.279  249/354:                  {0, 1}  0:(2131.26001,1080.24976,0.949999988,0.221191406)A   1: 2489.63574,808.438599,1.53750002,0.315917969 A?
1.279  250/354:                  {0, 1}  0:(2131.26001,1080.24976,0.949999988,0.221191406)A   1: 2489.13574,812.435791,1.53750002,0.314941406
1.288  251/354:                  {0, 1}  0:(2131.26001,1080.24976,0.949999988,0.222167969)A   1: 2489.63574,808.438599,1.53750002,0.314941406 A?
and another:
0.393   75/120:           MOVE   {1}                                                         1: 1211.57935,201.859818,1.22500002,0.260742188
0.402   76/120:  POINTER_DOWN(0) {0, 1}  0: 609.788269,479.666901,0.712500036,0.202636719 A!  1:(1211.57935,201.859818,1.22500002,0.260742188)
0.402   77/120:                  {0, 1}  0:(609.788269,479.666901,0.712500036,0.202636719)A   1: 1211.57935,203.858429,1.22500002,0.261718750
0.406   78/120:           MOVE   {0, 1}  0:(609.788269,479.666901,0.712500036,0.202636719)A   1: 1211.57935,204.857727,1.22500002,0.261718750 A!
0.410   79/120:                  {0, 1}  0:(609.788269,479.666901,0.725000024,0.204589844)A   1:(1211.57935,204.857727,1.22500002,0.261718750)A?
0.410   80/120:                  {0, 1}  0:(609.788269,479.666901,0.725000024,0.204589844)A   1: 1210.57971,205.857040,1.23750007,0.262695313
0.419   81/120:                  {0, 1}  0:(609.788269,479.666901,0.737500012,0.208007813)A   1: 1211.57935,204.857727,1.23750007,0.262695313 A?
0.419   82/120:           MOVE   {0, 1}  0:(609.788269,479.666901,0.737500012,0.208007813)A   1: 1209.58008,207.855652,1.25000000,0.263671875
0.427   83/120:                  {0, 1}  0:(609.788269,479.666901,0.762499988,0.210449219)A   1: 1211.57935,204.857727,1.25000000,0.263671875 A?
*/
// Here's where POINTER_DOWN 0 and 2 happened simultaneously... bug didn't happen (although that's not proof it can't)
/*
4.312  748/1016:                  {1}                                                            1: 2229.22607,863.400391,1.25000000,0.237792969
4.320  749/1016:                  {1}                                                            1: 2237.22314,856.405273,1.25000000,0.238769531
4.325  750/1016:           MOVE   {1}                                                            1: 2241.22168,852.907715,1.25000000,0.238769531
4.329  751/1016:           MOVE   {1}                                                            1: 2245.22046,848.410828,1.25000000,0.238769531
4.329  752/1016:  POINTER_DOWN(0) {0, 1}     0: 893.689697,1332.07495,0.725000024,0.185546875  !  1:[2245.22046,848.410828,1.25000000,0.238769531]
4.338  753/1016:  POINTER_DOWN(2) {0, 1, 2}  0:[893.689697,1332.07495,0.725000024,0.185546875]    1:[2245.22046,848.410828,1.25000000,0.238769531]    2: 630.781006,746.481628,0.612500012,0.197265625
4.338  754/1016:                  {0, 1, 2}  0:[893.689697,1332.07495,0.725000024,0.185546875]    1: 2254.21729,840.416382,1.25000000,0.238769531  !  2:[630.781006,746.481628,0.612500012,0.197265625] !
4.338  755/1016:           MOVE   {0, 1, 2}  0:(893.689697,1332.07495,0.750000000,0.186035156)    1:[2254.21729,840.416382,1.25000000,0.238769531] ?  2:[630.781006,746.481628,0.612500012,0.197265625]
4.346  756/1016:                  {0, 1, 2}  0:[893.689697,1332.07495,0.750000000,0.186035156]    1:[2254.21729,840.416382,1.25000000,0.238769531] ?  2:(630.781006,746.481628,0.637499988,0.197265625)
4.346  757/1016:                  {0, 1, 2}  0:[893.689697,1332.07495,0.750000000,0.186035156]    1: 2264.21387,832.421936,1.25000000,0.239746094     2:[630.781006,746.481628,0.637499988,0.197265625]
4.346  758/1016:           MOVE   {0, 1, 2}  0:(893.689697,1332.07495,0.762499988,0.188476563)    1:[2264.21387,832.421936,1.25000000,0.239746094]    2:[630.781006,746.481628,0.637499988,0.197265625]
*/
//
// Correlation with timestamp:
//      - on id 0, when it affects x,y, timestamp has typically *not* advanced, but once in a while it has advanced (not consistent)
//      - on id non-0, when it affects x,y, timestamp has typically (I think maybe always?)  advanced by 8 or 9 ms
//        (except when it happened on 0's POINTER_UP, in which case there was no time-advace. argh!), in which case there was no time-advace. argh!)
// Correlation with packet boundaries:
//      - none.
// Correlation with  event-happening-immedately-after-POINTER_DOWN(0)-is-MOVE-of-idNonzero-with-1-historical:
//      - when bugging, seems to be always that syndrome  (x,y of the historical may be same, maybe different)
//      - when not bugging: can be that, or can be something else, e.g. 2 historical, 2nd same as first.
//
// * HOW DO I WRAP THIS UP?
//   Need a heuristic that always works in practice.
//   It'll include things like:
//      - consider things safe again N ms (or N events?) after id0 or idnonzero went up
//   - Sloppy proposal #1:
//       - Get the anchors (POINTER_DOWN x,y for id0, the primary x,y of the following event for idNonzero),
//         and simply consider them forbidden (i.e. force x,y to not move, instead) until/unless safety is declared (or until a new POINTER_DOWN(0) occurs).
//         That might be fine-- even if we get a false positive, forbidding one location on the screen is probably not a problem.
//

package com.example.donhatch.multitouchbugworkaround;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import static com.example.donhatch.multitouchbugworkaround.CHECK.*;
import static com.example.donhatch.multitouchbugworkaround.STRINGIFY.STRINGIFY;


public class FixedOnTouchListener implements View.OnTouchListener {

  private static final String TAG = MultiTouchBugWorkaroundActivity.class.getSimpleName();  // XXX

  public View.OnTouchListener wrapped = null;
  public FixedOnTouchListener(View.OnTouchListener wrapped) {
    this.wrapped = wrapped;
  }

  private PrintWriter mTracePrintWriterOrNull = null;
  private ArrayList<StringBuilder> mAnnotationsOrNull = null;
  private ArrayList<StringBuilder> mFixedAnnotationsOrNull = null;
  private ArrayList<LogicalMotionEvent> mLogicalMotionEventsSinceFirstDown = null;
  private ArrayList<LogicalMotionEvent> mFixedLogicalMotionEventsSinceFirstDown = null;
  public void setTracePrintWriter(PrintWriter tracePrintWriter) {
    this.mTracePrintWriterOrNull = tracePrintWriter;
    this.mAnnotationsOrNull = tracePrintWriter!=null ? new ArrayList<StringBuilder>() : null;
    this.mLogicalMotionEventsSinceFirstDown = tracePrintWriter!=null ? new ArrayList<LogicalMotionEvent>() : null;
    this.mFixedLogicalMotionEventsSinceFirstDown = tracePrintWriter!=null ? new ArrayList<LogicalMotionEvent>() : null;
  }

  // Note, MotionEvent has an actionToString(), but it takes an unmasked action;
  // we want to take an unmasked action.
  public static String actionToString(int action) {
    if (action == MotionEvent.ACTION_DOWN) return "DOWN";
    if (action == MotionEvent.ACTION_POINTER_DOWN) return "POINTER_DOWN";
    if (action == MotionEvent.ACTION_MOVE) return "MOVE";
    if (action == MotionEvent.ACTION_POINTER_UP) return "POINTER_UP";
    if (action == MotionEvent.ACTION_UP) return "UP";
    //if (action == MotionEvent.ACTION_CANCEL) return "CANCEL";  // I've never seen this, I don't think
    throw new AssertionError("unrecognized MotionEvent action "+action);
  }

  final private int MAX_FINGERS = 10;
  final private float[] startXorig = new float[MAX_FINGERS];
  final private float[] startYorig = new float[MAX_FINGERS];
  final private float[] prevPrevXorig = new float[MAX_FINGERS];
  final private float[] prevPrevYorig = new float[MAX_FINGERS];
  final private float[] prevXorig = new float[MAX_FINGERS];
  final private float[] prevYorig = new float[MAX_FINGERS];
  final private float[] startXcorrected = new float[MAX_FINGERS];
  final private float[] startYcorrected = new float[MAX_FINGERS];
  final private float[] prevPrevXcorrected = new float[MAX_FINGERS];
  final private float[] prevPrevYcorrected = new float[MAX_FINGERS];
  final private float[] prevXcorrected = new float[MAX_FINGERS];
  final private float[] prevYcorrected = new float[MAX_FINGERS];
  public static class FixedMotionEvent {  // implements some of MotionEvent's methods

    // no one including FixedOnTouchListener should look at these
    final private MotionEvent unfixed;
    final private float[][] historicalAndActualX;
    final private float[][] historicalAndActualY;

    // FixedOnTouchListener may look at this; no one else should
    private FixedMotionEvent(MotionEvent unfixed,
                             float[/*pointerCount*/][/*historySize+1*/] historicalAndActualX,
                             float[/*pointerCount*/][/*historySize+1*/] historicalAndActualY) {
      this.unfixed = unfixed;
      this.historicalAndActualX = historicalAndActualX;
      this.historicalAndActualY = historicalAndActualY;
    }

    public int getPointerCount() { return unfixed.getPointerCount(); }
    public int getActionIndex() { return unfixed.getActionIndex(); }
    public int getActionMasked() { return unfixed.getActionMasked(); }
    public int getPointerId(int index) { return unfixed.getPointerId(index); }
    public int findPointerIndex(int id) { return unfixed.findPointerIndex(id); }
    public int getHistorySize() { return unfixed.getHistorySize(); }

    private final static boolean propagateFixFlag = true;
    public float getHistoricalX(int index, int h) {
      if (!propagateFixFlag) return unfixed.getHistoricalX(index, h);
      CHECK_LT(h, historicalAndActualX[index].length-1);
      return historicalAndActualX[index][h];
    }
    public float getHistoricalY(int index, int h) {
      if (!propagateFixFlag) return unfixed.getHistoricalY(index, h);
      CHECK_LT(h, historicalAndActualY[index].length-1);
      return historicalAndActualY[index][h];
    }
    public float getX(int index) {
      if (!propagateFixFlag) return unfixed.getX(index);
      return historicalAndActualX[index][historicalAndActualX[index].length-1];
    }
    public float getY(int index) {
      if (!propagateFixFlag) return unfixed.getY(index);
      return historicalAndActualY[index][historicalAndActualY[index].length-1];
    }
  }

  // To aid analysis.
  private static class LogicalMotionEvent {
    public boolean isHistorical;
    public long eventTimeMillis;  // "in the uptimeMillis() time base"
    public int action;
    public int actionId;
    public int ids[];
    public MotionEvent.PointerCoords[] all_axis_values;

    // Convenience accessors for the commonly used ones
    public float x(int id) { return all_axis_values[id].getAxisValue(MotionEvent.AXIS_X); }
    public float y(int id) { return all_axis_values[id].getAxisValue(MotionEvent.AXIS_Y); }
    public float pressure(int id) { return all_axis_values[id].getAxisValue(MotionEvent.AXIS_PRESSURE); }
    public float size(int id) { return all_axis_values[id].getAxisValue(MotionEvent.AXIS_SIZE); }

    // all arrays assumed to be immutable.
    public LogicalMotionEvent(boolean isHistorical, long eventTimeMillis, int action, int actionId, int ids[], MotionEvent.PointerCoords[] all_axis_values) {
      this.isHistorical = isHistorical;
      this.eventTimeMillis = eventTimeMillis;
      this.action = action;
      this.actionId = actionId;
      this.ids = ids;
      this.all_axis_values = all_axis_values;
      for (int id : ids) {
        CHECK_LE(0, id);
        CHECK_LE(id, all_axis_values.length);
        CHECK(all_axis_values[id] != null);
      }
    }
    // Breaks motionEvent down into LogicalMotionEvents and appends them to list.
    public static void breakDown(MotionEvent motionEvent, ArrayList<LogicalMotionEvent> list) {
      final int action = motionEvent.getActionMasked();
      final int actionId = motionEvent.getPointerId(motionEvent.getActionIndex());
      final int pointerCount = motionEvent.getPointerCount();
      final int historySize = motionEvent.getHistorySize();
      final int[] ids = new int[pointerCount];
      int maxIdOccurring = -1;
      for (int index = 0; index < pointerCount; ++index) {
        ids[index] = motionEvent.getPointerId(index);
        maxIdOccurring = Math.max(maxIdOccurring, ids[index]);
      }
      for (int h = 0; h < historySize+1; ++h) {
        final long eventTimeMillis = h==historySize ? motionEvent.getEventTime() : motionEvent.getHistoricalEventTime(h);
        final float[] x = new float[maxIdOccurring+1];
        final float[] y = new float[maxIdOccurring+1];
        final float[] pressure = new float[maxIdOccurring+1];
        final float[] size = new float[maxIdOccurring+1];
        final MotionEvent.PointerCoords[] all_axis_values = new MotionEvent.PointerCoords[maxIdOccurring+1];
        for (int index = 0; index < pointerCount; ++index) {
          final int id = ids[index];
          CHECK(all_axis_values[id] == null);  // I guess this could fail if there are dup ids for some reason
          all_axis_values[id] = new MotionEvent.PointerCoords();
          if (h==historySize) {
            motionEvent.getPointerCoords(index, all_axis_values[id]);
          } else {
            motionEvent.getHistoricalPointerCoords(index, h, all_axis_values[id]);
          }
          // Empirically, AXIS_ORIENTATION is 0, +-pi/2, or pi, depending on current device orientation (of course)
          CHECK_EQ(all_axis_values[id].getAxisValue(MotionEvent.AXIS_RELATIVE_X), 0.f);  // XXX not sure if this is reliable on all devices, but it's what I always get
          CHECK_EQ(all_axis_values[id].getAxisValue(MotionEvent.AXIS_RELATIVE_Y), 0.f);  // XXX not sure if this is reliable on all devices, but it's what I always get
          // also, rawX()/rawY() doesn't seem to be helpful: (1) can't query it per-history nor per-pointer, (2) the bug apparently affects this too so it doesn't give any clues
        }
        list.add(new LogicalMotionEvent(h<historySize, eventTimeMillis, action, actionId, ids, all_axis_values));
      }
    }  // breakDown

    // A B C AA AB AC BA BB BC CA CB CC AAA AAB ...
    private static String GenerateLabel(int labelIndex) {
      int nDigits = 0;
      if (labelIndex < 26) {
        return String.format("%c", 'A'+labelIndex);
      }
      if (labelIndex < 26*26) {
        return String.format("%c%c", 'A'+(labelIndex/26), 'A'+(labelIndex%26));
      }
      if (labelIndex < 26*26*26) {
        return String.format("%c%c", 'A'+(labelIndex/26), 'A'+(labelIndex%26));
      }
      if (labelIndex < 26*26*26) {
        return String.format("%c%c%c", 'A'+(labelIndex/26/26), 'A'+(labelIndex/26%26), 'A'+(labelIndex%26));
      }
      if (labelIndex < 26*26*26*26) {
        return String.format("%c%c%c%c", 'A'+(labelIndex/26/26/26), 'A'+(labelIndex/26/26%26), 'A'+(labelIndex/26%26), 'A'+(labelIndex%26));
      }
      CHECK(false);
      return null;
    }

    // PointerCoords.equals() doesn't work (just tests pointer equality)
    private static boolean PointerCoordsEquals(MotionEvent.PointerCoords a, MotionEvent.PointerCoords b) {
      int verboseLevel = 0;
      if (verboseLevel >= 1) Log.i(TAG, "in PointerCoordsEquals(a, b)");
      boolean answer = true;  // until proven otherwise
      final int NUM_AXES = 64;  // empirically, getAxisValue(>=64) throws "java.lang.IllegalArgumentException: Axis out of range.".
      for (int axis = 0; axis < NUM_AXES; ++axis) {
        float aValue = a.getAxisValue(axis);
        float bValue = b.getAxisValue(axis);
        if (verboseLevel >= 1) {
          Log.i(TAG, "  "+axis+": "+aValue+" "+(aValue==bValue?"==":"!=")+" "+bValue+" ("+MotionEvent.axisToString(axis)+")");
          continue;
        }
        if (aValue != bValue) {
          answer = false;
          if (!(verboseLevel >= 1)) break;
        }
      }
      if (verboseLevel >= 1) Log.i(TAG, "out PointerCoordsEquals(a, b), returning "+answer);
      return answer;
    }

    // STRINGIFY uses ", ", we want "," instead
    public static String STRINGIFY_COMPACT(int[] array) {
      StringBuilder sb = new StringBuilder("{");
      for (int i = 0; i < array.length; ++i) {
        if (i != 0) sb.append(",");
        sb.append(array[i]);
      }
      sb.append("}");
      return sb.toString();
    }

    public static String dumpString(ArrayList<LogicalMotionEvent> list) {
      final int verboseLevel = 0;  // 0: nothing, 1: in/out, 2: some gory details
      if (verboseLevel >= 1) Log.i(TAG, "in LogicalMotionEvent.dumpString("+list.size()+" logical events)");
      StringBuilder answer = new StringBuilder();

      int n = list.size();

      int idsWidth = 0;
      int max_id_occurring = -1;
      for (int i = 0; i < n; ++i) {
        final int ids[] = list.get(i).ids;
        idsWidth = Math.max(idsWidth, STRINGIFY_COMPACT(ids).length());
        for (final int id: ids) {
          max_id_occurring = Math.max(max_id_occurring, id);
        }
      }

      // For each id,
      // whenever there are 2 or more distinct runs with the same coords
      // within a given down-period of that id,
      // make a label for those coords.
      final String[][] id2index2label = new String[max_id_occurring+1][n];  // nulls initially
      {
        for (int id = 0; id <= max_id_occurring; ++id) {
          if (verboseLevel >= 2) Log.i(TAG, "      processing id "+id);
          String[] index2label = new String[n];  // nulls initially

          String[] index2fingerprint = new String[n];  // nulls initially
          for (int i = 0; i < n; ++i) {
            LogicalMotionEvent e = list.get(i);
            if (id < e.all_axis_values.length && e.all_axis_values[id] != null) {
              String fingerprint = e.x(id)+","+e.y(id);
              index2fingerprint[i] = fingerprint;
            }
          }
          if (verboseLevel >= 2) Log.i(TAG, "          index2fingerprint = "+STRINGIFY(index2fingerprint));

          // Focusing on this id,
          // each sequence of pointer-down,moves,pointer-up
          // is its own label scope.

          for (int i0 = 0; i0 < n;) {
            // advance i0, if necessary, to something that has a fingerprint
            while (i0 < n && index2fingerprint[i0] == null) i0++;
            if (i0 == n) break;
            int i1 = i0+1;
            // advance i1 to something that does *not* have a fingerprint
            while (i1 < n && index2fingerprint[i1] != null) i1++;

            // [i0,i1) is now a range beginning with this pointer down
            // and ending with this pointer up.
            // That's a scope; assign labels within it.

            if (verboseLevel >= 2) Log.i(TAG, "              ["+i0+","+i1+")");

            HashMap<String,int[]> fingerprint2nRunsHolder = new HashMap<String,int[]>();
            // Initialize to zeros
            for (String fingerprint : index2fingerprint) {
              if (fingerprint != null) {
                if (fingerprint2nRunsHolder.get(fingerprint) == null) {
                  fingerprint2nRunsHolder.put(fingerprint, new int[]{0});
                }
              }
            }
            for (int i = i0; i < i1; ++i) {
              String fingerprint = index2fingerprint[i];
              if (fingerprint != null) {
                boolean isRunStart = (i==i0 || index2fingerprint[i-1]==null || !fingerprint.equals(index2fingerprint[i-1]));
                if (isRunStart) {
                  fingerprint2nRunsHolder.get(fingerprint)[0]++;
                }
              }
            }
            if (verboseLevel >= 2) Log.i(TAG, "                  fingerprint2nRunsHolder = "+STRINGIFY(fingerprint2nRunsHolder));
            HashMap<String,String> fingerprint2label = new HashMap<String,String>();
            int nLabels = 0;
            for (int i = i0; i < i1; ++i) {
              String fingerprint = index2fingerprint[i];
              if (fingerprint != null) {
                boolean isRunStart = (i==i0 || index2fingerprint[i-1]!=null || !fingerprint.equals(index2fingerprint[i-1]));
                if (isRunStart) {
                  int nRuns = fingerprint2nRunsHolder.get(fingerprint)[0];
                  if (nRuns >= 2) {
                    String label = fingerprint2label.get(fingerprint);
                    if (label == null) {
                      label = GenerateLabel(nLabels++);
                      fingerprint2label.put(fingerprint, label);
                    }
                  }
                }
                index2label[i] = fingerprint2label.get(fingerprint);  // may be null
              }
            }
            // Advance i0 to end of this sequence of things that have fingerprints.
            // Top of loop will advance it to beginning of next sequence that has fingerprints.
            i0 = i1;
          }
          if (verboseLevel >= 2) Log.i(TAG, "          index2label = "+STRINGIFY(index2label));
          id2index2label[id] = index2label;
          if (verboseLevel >= 2) Log.i(TAG, "      done processing id "+id);
        }
      }

      int idOfInterest = -1; // never zero.  if positive, either might be bugging or definitely bugging.
      float xOfInterestForId0 = Float.NaN;  // if non-nan, might be bugging or definitely bugging.
      float yOfInterestForId0 = Float.NaN;  // if non-nan, might be bugging or definitely bugging.
      float xOfInterestForIdNonzero = Float.NaN;
      float yOfInterestForIdNonzero = Float.NaN;
      boolean knownToBeSafe = true;  // actually the same as idOfInterest==-1
      boolean knownToBeBugging = false;
      final float[] mostRecentNonSuspiciousX = new float[max_id_occurring+1];
      final float[] mostRecentNonSuspiciousY = new float[max_id_occurring+1];

      long refTimeMillis = list.get(0).eventTimeMillis;
      for (int i = 0; i < n;  ++i) {
        LogicalMotionEvent e = list.get(i);

        boolean justNowGotCoordsOfInterestForId0 = false;
        boolean justNowGotCoordsOfInterestForIdNonzero = false;

        if (e.action == MotionEvent.ACTION_POINTER_DOWN
         && e.actionId == 0
         && e.ids.length == 2) {
          CHECK_EQ(e.ids[0], 0);
          // Might be bugging.  Yellow alert.
          knownToBeSafe = false;
          knownToBeBugging = false;
          idOfInterest = e.ids[1];
          xOfInterestForId0 = e.x(0);
          yOfInterestForId0 = e.y(0);
          xOfInterestForIdNonzero = Float.NaN;
          yOfInterestForIdNonzero = Float.NaN;
          justNowGotCoordsOfInterestForId0 = true;
        }
        // If the next packet is not a POINTER_UP(id1),
        // then the next packet seems to always have size 2,
        // and the last (i.e. non-historical) entry in it is the buggy non-0-id item, if any
        if (i >= 2) {
          LogicalMotionEvent ePrevPrev = list.get(i-2);
          LogicalMotionEvent ePrev = list.get(i-1);
          if (ePrevPrev.action == MotionEvent.ACTION_POINTER_DOWN
           && ePrevPrev.actionId == 0
           && ePrevPrev.ids.length == 2
           && ePrev.action == MotionEvent.ACTION_MOVE) {
            CHECK_GE(idOfInterest, 0);
            CHECK_LT(idOfInterest, e.all_axis_values.length);
            xOfInterestForIdNonzero = e.x(idOfInterest);
            yOfInterestForIdNonzero = e.y(idOfInterest);
            justNowGotCoordsOfInterestForIdNonzero = true;
          }
        }

        StringBuilder sb = new StringBuilder();
        long relativeTimeMillis = e.eventTimeMillis - refTimeMillis;
        if (e.isHistorical) CHECK_EQ(e.action, MotionEvent.ACTION_MOVE);
        if (e.action==MotionEvent.ACTION_MOVE) CHECK_EQ(e.actionId, e.ids[0]);
        sb.append(String.format("          %3d.%03d %4d/%d: %16s %-"+idsWidth+"s",
                                relativeTimeMillis/1000,
                                relativeTimeMillis%1000,
                                i, n,
                                (e.action==MotionEvent.ACTION_MOVE && e.isHistorical ? "" :
                                (actionToString(e.action)+(e.action==MotionEvent.ACTION_MOVE?"  ":"("+e.actionId+")"))),
                                STRINGIFY_COMPACT(e.ids)));
        for (int id = 0; id < e.all_axis_values.length; ++id) {
          if (e.all_axis_values[id] == null) {
            //sb.append(String.format("%29s", ""));
            sb.append(String.format("%52s", "", ""));
          } else {
            //String coordsString = String.format("%.9g,%.9g", e.x(id), e.y(id));
            String coordsString = String.format("%.9g,%.9g,%.9g,%.9g", e.x(id), e.y(id), e.pressure(id), e.size(id));

            boolean parenthesized = false;
            if (i >= 1) {
              LogicalMotionEvent ePrev = list.get(i-1);
              if (id < ePrev.all_axis_values.length
               && ePrev.all_axis_values[id] != null
               && e.x(id) == ePrev.x(id)
               && e.y(id) == ePrev.y(id)) {
                if (PointerCoordsEquals(e.all_axis_values[id], ePrev.all_axis_values[id])) {
                  coordsString = "["+coordsString+"]";
                } else {
                  coordsString = "("+coordsString+")";
                }
                parenthesized = true;
              }
            }
            if (!parenthesized) {
                coordsString = " "+coordsString+" ";
            }
            coordsString = String.format(" %2d:%s", id, coordsString);

            String label = id2index2label[id][i];
            if (label != null) {
              coordsString += label;
            } else {
              coordsString += " ";
            }

            if (true) {
              // Look for smoking gun: pressure,size same as previous but x,y *not* same as most-recent-good.
              // Bleah, it's not conclusive--- lots of false positives, i.e. pressure,size same as previous
              // and x,y legitimately different from previous.
              // Haven't seen any false negatives though.
              boolean foundSmokingGun = false;
              if (i > 0) {
                LogicalMotionEvent ePrev = list.get(i-1);
                // XXX this type need not be MOVE in order for bug to manifest..  must previous type be MOVE in order for bug to manifest?

                if (id < ePrev.all_axis_values.length
                 && ePrev.all_axis_values[id] != null
                 && e.pressure(id) == ePrev.pressure(id)
                 && e.size(id) == ePrev.size(id)
                 //&& (e.x(id) != ePrev.x(id) || e.y(id) != ePrev.y(id))   // XXX wrong!  need to be comparing with previous *corrected*
                 && (e.x(id) != mostRecentNonSuspiciousX[id] || e.y(id) != mostRecentNonSuspiciousY[id])   // XXX wrong!  need to be comparing with previous *corrected*
                 ) {
                  foundSmokingGun = true;
                }
              }

              if (knownToBeSafe) {
                //Log.i(TAG, "    KILLING QUESTION MARK on "+id+" BECAUSE knownToBeSafe");
                foundSmokingGun = false;
              }
              if (id != 0 && id != idOfInterest) { // those are the only suspects
                //Log.i(TAG, "    KILLING QUESTION MARK on "+id+" BECAUSE not a suspect");
                foundSmokingGun = false;
              }
              if (id == 0 && (e.x(0) != xOfInterestForId0 || e.y(0) != yOfInterestForId0)) {  // id 0 only bugs on the coords on which it went down
                //Log.i(TAG, "    KILLING QUESTION MARK on "+id+" BECAUSE the 0 suepect and not the coords on which it went down");
                foundSmokingGun = false;
              }
              if (id != 0 && (e.x(id) != xOfInterestForIdNonzero || e.y(id) != yOfInterestForIdNonzero)) {  // id nonzero only bugs on those coords. when not decided yet, it doesn't bug
                //Log.i(TAG, "    KILLING QUESTION MARK on "+id+" BECAUSE the nonzero suspect and not the coords two after the POINTER_DOWN(0) event which are "+xOfInterestForIdNonzero+","+yOfInterestForIdNonzero+"");
                foundSmokingGun = false;
              }

              if (!foundSmokingGun) {
                mostRecentNonSuspiciousX[id] = e.x(id);
                mostRecentNonSuspiciousY[id] = e.y(id);
              }

              if (id==0 && justNowGotCoordsOfInterestForId0) {
                coordsString += "!";
              } else if (id!=0 && justNowGotCoordsOfInterestForIdNonzero) {
                coordsString += "!";
              } else if (foundSmokingGun) {
                coordsString += "?";
              } else {
                coordsString += " ";
              }
            }
            sb.append(coordsString);
          }
        }
        answer.append(sb);
        answer.append("\n");
      }
      if (verboseLevel >= 1) Log.i(TAG, "out LogicalMotionEvent.dumpString("+list.size()+" logical events)");
      return answer.toString();
    }  // dump

    // Argh!  Calling Log.i on the multiline dump string causes it to be truncated *very* early (like, dozens instead of hundreds).
    public static void LogMultiline(String tag, String s) {
      for (String line : s.split("\n")) {
        Log.i(tag, line);
      }
    }

    // Read back in a string produced by dumpString().
    public static ArrayList<LogicalMotionEvent> parseDump(String dumpString) throws java.text.ParseException {
      final int verboseLevel = 0;  // 0: nothing, 1: in/out, 2: gory details
      if (verboseLevel >= 1) Log.i(TAG, "            in parseDump");
      final ArrayList<LogicalMotionEvent> answer = new ArrayList<LogicalMotionEvent>();
      final String[] lines = dumpString.split("\n");
      // "0.189   28/217:                  {0}     0: 2241.22192,512.643982,1.27499998,0.246582031"
      final Pattern pattern = Pattern.compile(
          "^" +
          "\\s*" +
          "(?<logcatstuff>\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+)?" +  // 0
          "(?<timestamp>[\\d.]+)\\s+" +  // 1
          "(?<i>\\d+)/(?<n>\\d+):\\s+" +  // 2,3
          "((?<action>(\\S+))\\s+)?" +  // 4,5
          "(?<ids>\\{.*\\})" +  // 6
          "(?<perIdData>(?<lastPerIdData>\\s+(\\d+:[^:/]+))+)" +  // 7,8
          "\\s*(?<comment>//.*)?" +  // 9
          "$");
      final Pattern perIdDataPattern = Pattern.compile(
          // "0: 2288.70557,477.668274,1.28750002,0.246582031 AA"
          // "0:[2288.70557,477.668274,1.28750002,0.246582031]AA"
          "\\s*(?<id>\\d+):\\s*(?<stuff>[^ A-Z]+)(?<labelMaybe>\\s*[A-Z]+)?(?<punctuationMaybe>[!?])?(\\s*)"
      );
      // I wish: captured "+" and "*" would give arrays back.  That would simplify a lot.
      for (int iLine = 0; iLine < lines.length; ++iLine) {
        final String line = lines[iLine];
        if (verboseLevel >= 2) Log.i(TAG, "                  line "+iLine+"/"+lines.length+": "+STRINGIFY(line));
        final Matcher matcher = pattern.matcher(line);
        //if (verboseLevel >= 2) Log.i(TAG, "                      match = "+matcher);
        if (!matcher.matches()) {
          throw new java.text.ParseException("Unrecognized line "+STRINGIFY(line), 0);
        }
        CHECK_EQ(matcher.groupCount(), 12);
        if (verboseLevel >= 2) Log.i(TAG, "                      logcatstuff = "+STRINGIFY(matcher.group("logcatstuff")));
        if (verboseLevel >= 2) Log.i(TAG, "                      timestamp = "+STRINGIFY(matcher.group("timestamp")));
        if (verboseLevel >= 2) Log.i(TAG, "                      i = "+STRINGIFY(matcher.group("i")));
        if (verboseLevel >= 2) Log.i(TAG, "                      n = "+STRINGIFY(matcher.group("n")));
        if (verboseLevel >= 2) Log.i(TAG, "                      action = "+STRINGIFY(matcher.group("action")));
        if (verboseLevel >= 2) Log.i(TAG, "                      ids = "+STRINGIFY(matcher.group("ids")));
        if (verboseLevel >= 2) Log.i(TAG, "                      perIdData = "+STRINGIFY(matcher.group("perIdData")));
        if (verboseLevel >= 2) Log.i(TAG, "                      lastPerIdData = "+STRINGIFY(matcher.group("lastPerIdData")));
        if (verboseLevel >= 2) Log.i(TAG, "                      comment = "+STRINGIFY(matcher.group("comment")));


        ArrayList<Integer> idsArrayList = new ArrayList<Integer>();
        ArrayList<float[]> valuesArrayList = new ArrayList<float[]>();

        final String perIdData = matcher.group("perIdData");
        // perIdData = "0: 2332.19019,448.688416,1.30000007,0.248535156 A!    1:[976.660889,614.573181,1.77499998,0.309570313]"
        final Matcher perIdDataMatcher = perIdDataPattern.matcher(perIdData);
        int pos = 0;
        while (perIdDataMatcher.find()) {
          CHECK_EQ(perIdDataMatcher.start(), pos);  // i.e. didn't skip anything
          CHECK_EQ(perIdDataMatcher.groupCount(), 5);
          String perIdDatum = perIdDataMatcher.group();
          String id = perIdDataMatcher.group("id");
          String stuff = perIdDataMatcher.group("stuff");
          String labelMaybe = perIdDataMatcher.group("labelMaybe");
          String punctuationMaybe = perIdDataMatcher.group("punctuationMaybe");
          if ((stuff.startsWith("(") && stuff.endsWith(")"))
           || (stuff.startsWith("[") && stuff.endsWith("]"))) {
            stuff = stuff.substring(/*start=*/1, /*end=*/stuff.length()-1);
          }
          String[] stuffTokens = stuff.split(",");
          if (verboseLevel >= 2) Log.i(TAG, "                          perIdDatum = "+STRINGIFY(perIdDatum));
          if (verboseLevel >= 2) Log.i(TAG, "                              id = "+STRINGIFY(id));
          if (verboseLevel >= 2) Log.i(TAG, "                              stuff = "+STRINGIFY(stuff));
          if (verboseLevel >= 2) Log.i(TAG, "                                  stuffTokens="+STRINGIFY(stuffTokens));
          if (verboseLevel >= 2) Log.i(TAG, "                              labelMaybe = "+STRINGIFY(labelMaybe));
          if (verboseLevel >= 2) Log.i(TAG, "                              punctuationMaybe = "+STRINGIFY(punctuationMaybe));
          float[] values = new float[stuffTokens.length];
          for (int i = 0; i < stuffTokens.length; ++i) {
            values[i] = Float.parseFloat(stuffTokens[i]);
          }
          idsArrayList.add(Integer.parseInt(id));
          valuesArrayList.add(values);

          pos = perIdDataMatcher.end();
        }
        CHECK(perIdDataMatcher.hitEnd());

        final long eventTimeMillis = (long)Math.round(Double.parseDouble(matcher.group("timestamp")) * 1000.);
        final int[] ids = new int[idsArrayList.size()];
        int max_id_occurring = -1;
        for (int index = 0; index < ids.length; ++index) {
          ids[index] = idsArrayList.get(index);
          max_id_occurring = Math.max(max_id_occurring, ids[index]);
        }
        CHECK_LT(max_id_occurring, 100);  // make sure it's not ridiculous
        final MotionEvent.PointerCoords[] all_axis_values = new MotionEvent.PointerCoords[max_id_occurring+1];
        for (int index = 0; index < ids.length; ++index) {
          final int id = ids[index];
          all_axis_values[id] = new MotionEvent.PointerCoords();
          final float[] values = valuesArrayList.get(index);
          CHECK_EQ(values.length, 4);
          all_axis_values[id].setAxisValue(MotionEvent.AXIS_X, values[0]);
          all_axis_values[id].setAxisValue(MotionEvent.AXIS_Y, values[1]);
          all_axis_values[id].setAxisValue(MotionEvent.AXIS_PRESSURE, values[2]);
          all_axis_values[id].setAxisValue(MotionEvent.AXIS_SIZE, values[3]);
        }
        CHECK_GE(ids.length, 1);

        if (verboseLevel >= 2) Log.i(TAG, "                              ids = "+STRINGIFY_COMPACT(ids));
        if (true) {
          // Check that ids matches which columns were populated
          final String idsString = matcher.group("ids");
          CHECK(idsString.startsWith("{"));
          CHECK(idsString.endsWith("}"));
          final String[] idTokens = idsString.substring(/*start=*/1, /*end=*/idsString.length()-1).split(",\\s*");
          final int shouldBeIds[] = new int[idTokens.length];
          for (int i = 0; i < idTokens.length; ++i) {
            shouldBeIds[i] = Integer.parseInt(idTokens[i]);
          }
          if (verboseLevel >= 2) Log.i(TAG, "                              shouldBeIds = "+STRINGIFY_COMPACT(shouldBeIds));
          if (!STRINGIFY_COMPACT(ids).equals(STRINGIFY_COMPACT(shouldBeIds))) {
            throw new AssertionError("claimed ids "+STRINGIFY_COMPACT(shouldBeIds)+", actually occurring ids "+STRINGIFY_COMPACT(ids));

          }
        }

        boolean isHistorical;
        int action, actionId;
        {
          String actionString = matcher.group("action");
          if (actionString == null) {
            isHistorical = true;
            action = MotionEvent.ACTION_MOVE;
            actionId = ids[0];
          } else if (actionString.equals("MOVE")) {
            isHistorical = false;
            action = MotionEvent.ACTION_MOVE;
            actionId = ids[0];
          } else {
            isHistorical = false;
            CHECK(actionString.endsWith(")"));
            CHECK(actionString.charAt(actionString.length()-3) == '(');  // CBB: assumes max id <= 9
      if (verboseLevel >= 1) Log.i(TAG, "            WTF "+(actionString.length()-2));
            actionId = Integer.parseInt(actionString.substring(/*start=*/actionString.length()-2, /*end=*/actionString.length()-1));
            String actionName = actionString.substring(/*start=*/0, /*end=*/actionString.length()-3);
            if (actionName.equals("DOWN")) action = MotionEvent.ACTION_DOWN;
            else if (actionName.equals("POINTER_DOWN")) action = MotionEvent.ACTION_POINTER_DOWN;
            else if (actionName.equals("POINTER_UP")) action = MotionEvent.ACTION_POINTER_UP;
            else if (actionName.equals("UP")) action = MotionEvent.ACTION_UP;
            else throw new AssertionError("unrecognized action "+STRINGIFY(actionName));
          }
        }
        answer.add(new LogicalMotionEvent(isHistorical, eventTimeMillis, action, actionId, ids, all_axis_values));
      }
      if (verboseLevel >= 1) Log.i(TAG, "            out parseDump, returning "+answer.size()+" logical events");
      return answer;
    }  // parseDump

    // Tests a case that I originally had to debug: missing labels.
    public static void testParseDump() {
      final int verboseLevel = 1;  // reasonable to be 1 for real
      if (verboseLevel >= 1) Log.i(TAG, "        in LogicalMotionEvent.testParseDump");
      final String[] dumpStrings = {
          // This was a bug (missing labels), is no longer
          String.join("\n",
              "0.189   28/217:                  {0}     0: 2241.22192,512.643982,1.27499998,0.246582031",
              "0.194   29/217:           MOVE   {0}     0: 2250.21875,505.648834,1.27499998,0.246582031",
              "0.198   30/217:           MOVE   {0}     0: 2260.21533,498.653717,1.27499998,0.246582031",
              "0.206   31/217:  POINTER_DOWN(1) {0, 1}  0:[2260.21533,498.653717,1.27499998,0.246582031]    1: 976.660889,614.573181,1.87500000,0.322265625    // <- should be A",
              "0.206   32/217:                  {0, 1}  0: 2279.20874,484.663422,1.28750002,0.246582031     1:[976.660889,614.573181,1.87500000,0.322265625]    // <- should be A",
              "0.210   33/217:           MOVE   {0, 1}  0: 2288.70557,477.668274,1.28750002,0.246582031 A   1:[976.660889,614.573181,1.87500000,0.322265625]    // <- should be A",
              "0.214   34/217:                  {0, 1}  0:[2288.70557,477.668274,1.28750002,0.246582031]A   1:(976.660889,614.573181,1.85000002,0.317382813)    // <- should be A",
              "0.214   35/217:           MOVE   {0, 1}  0: 2297.20239,471.672455,1.28750002,0.247558594     1:[976.660889,614.573181,1.85000002,0.317382813]    // <- should be A",
              "0.223   36/217:                  {0, 1}  0: 2288.70557,477.668274,1.28750002,0.247558594 A   1:(976.660889,614.573181,1.81250000,0.311523438)    // <- should be A",
              "0.223   37/217:           MOVE   {0, 1}  0: 2315.19629,459.680756,1.30000007,0.247558594     1:[976.660889,614.573181,1.81250000,0.311523438]    // <- should be A",
              "0.231   38/217:                  {0, 1}  0: 2288.70557,477.668274,1.30000007,0.247558594 A   1:(976.660889,614.573181,1.77499998,0.309570313)    // <- should be A",
              "0.231   39/217:           MOVE   {0, 1}  0: 2332.19019,448.688416,1.30000007,0.248535156     1:[976.660889,614.573181,1.77499998,0.309570313]    // <- should be A",
              "0.240   40/217:                  {0, 1}  0: 2288.70557,477.668274,1.30000007,0.248535156 A   1:(976.660889,614.573181,1.72500002,0.303710938)    // <- should be A",
              "0.240   41/217:                  {0, 1}  0: 2349.18433,438.695343,1.31250000,0.248535156     1:[976.660889,614.573181,1.72500002,0.303710938]    // <- should be A",
              "0.248   42/217:           MOVE   {0, 1}  0: 2288.70557,477.668274,1.31250000,0.248535156 A   1: 938.674072,624.566284,1.68750000,0.301757813",
              "0.248   43/217:           MOVE   {0, 1}  0: 2365.17871,429.701599,1.32500005,0.247558594     1: 976.660889,614.573181,1.68750000,0.301757813     // <- should be A",
              "0.257   44/217:                  {0, 1}  0: 2288.70557,477.668274,1.32500005,0.247558594 A   1: 877.695251,645.551697,1.64999998,0.295898438",
              "0.257   45/217:           MOVE   {0, 1}  0: 2381.17334,421.707153,1.32500005,0.249511719     1: 976.660889,614.573181,1.64999998,0.295898438    // <- should be A",
              "0.265   46/217:                  {0, 1}  0: 2288.70557,477.668274,1.32500005,0.249511719 A   1: 829.711914,665.537842,1.61250007,0.293457031",
              "0.265   47/217:           MOVE   {0, 1}  0: 2396.16797,413.712708,1.33749998,0.249511719     1: 976.660889,614.573181,1.61250007,0.293457031    // <- should be A",
              "0.274   48/217:                  {0, 1}  0: 2288.70557,477.668274,1.33749998,0.249511719 A   1: 785.727173,689.521179,1.56250000,0.288574219",
              "0.274   49/217:                  {0, 1}  0: 2410.16309,406.717560,1.33749998,0.248046875     1: 976.660889,614.573181,1.56250000,0.288574219    // <- should be A",
              "0.282   50/217:           MOVE   {0, 1}  0: 2288.70557,477.668274,1.33749998,0.248046875 A   1: 752.738647,713.504517,1.52499998,0.286132813",
              "0.282   51/217:           MOVE   {0, 1}  0: 2424.15845,400.721710,1.35000002,0.248046875     1: 976.660889,614.573181,1.52499998,0.286132813    // <- should be A",
              "0.291   52/217:                  {0, 1}  0: 2288.70557,477.668274,1.35000002,0.248046875 A   1: 733.745239,737.487854,1.48750007,0.281250000",
              "0.291   53/217:           MOVE   {0, 1}  0: 2437.15381,394.725891,1.35000002,0.249023438     1: 976.660889,614.573181,1.48750007,0.281250000    // <- should be A",
              "0.299   54/217:                  {0, 1}  0: 2288.70557,477.668274,1.35000002,0.249023438 A   1: 723.748718,761.471191,1.45000005,0.279296875",
              "0.299   55/217:           MOVE   {0, 1}  0: 2449.14966,389.729340,1.36250007,0.249023438     1: 976.660889,614.573181,1.45000005,0.279296875    // <- should be A",
              "0.308   56/217:                  {0, 1}  0: 2288.70557,477.668274,1.36250007,0.249023438 A   1: 718.750427,786.453857,1.41250002,0.274902344",
              "0.308   57/217:           MOVE   {0, 1}  0: 2460.14575,384.732819,1.36250007,0.249023438     1: 976.660889,614.573181,1.41250002,0.274902344    // <- should be A",
              "0.316   58/217:                  {0, 1}  0: 2288.70557,477.668274,1.36250007,0.249023438 A   1: 709.753601,809.437866,1.35000002,0.270019531",
              "0.316   59/217:           MOVE   {0, 1}  0: 2468.14307,379.736298,1.37500000,0.250000000     1: 976.660889,614.573181,1.35000002,0.270019531    // <- should be A",
              "0.323   60/217:           MOVE   {0, 1}  0: 2476.14038,375.739075,1.37500000,0.251953125     1:[976.660889,614.573181,1.35000002,0.270019531]    // <- should be A",
              "0.332   61/217:    POINTER_UP(1) {0, 1}  0:[2476.14038,375.739075,1.37500000,0.251953125]    1:[976.660889,614.573181,1.35000002,0.270019531]    // <- should be A",
              "0.332   62/217:                  {0}     0: 2484.13745,371.741852,1.38750005,0.250976563",
              "0.339   63/217:           MOVE   {0}     0: 2489.33521,369.143951,1.38750005,0.250976563",
              "0.339   63/217:           MOVE   {0, 1}  0: 2489.33521,369.143951,1.38750005,0.250976563 FAKELABEL? 1:[976.660889,614.573181,1.35000002,0.270019531] FAKE   // fake fake ",
            ""),
        // This was a bug (column 0 should have been empty)
          String.join("\n",
              "04-27 07:07:33.090  9196  9196 I MultiTouchBugWorkaroundActivity:             0.873  238/262:                  {0, 1, 2}  0: 1004.65118,1006.30115,1.00000000,0.211914063     1:[2156.25146,716.502441,0.750000000,0.208007813]    2:[2127.26147,1182.17908,0.837500036,0.196777344]",
              "04-27 07:07:33.091  9196  9196 I MultiTouchBugWorkaroundActivity:             0.881  239/262:           MOVE   {0, 1, 2}  0:[1004.65118,1006.30115,1.00000000,0.211914063]    1:[2156.25146,716.502441,0.750000000,0.208007813]    2: 2128.26099,1181.17969,0.850000024,0.197753906",
              "04-27 07:07:33.091  9196  9196 I MultiTouchBugWorkaroundActivity:             0.881  240/262:    POINTER_UP(0) {0, 1, 2}  0:[1004.65118,1006.30115,1.00000000,0.211914063]    1:[2156.25146,716.502441,0.750000000,0.208007813]    2:[2128.26099,1181.17969,0.850000024,0.197753906]",
              "04-27 07:07:33.091  9196  9196 I MultiTouchBugWorkaroundActivity:             0.889  241/262:                  {1, 2}                                                        1:[2156.25146,716.502441,0.750000000,0.208007813]    2:[2128.26099,1181.17969,0.850000024,0.197753906]",
              "04-27 07:07:33.091  9196  9196 I MultiTouchBugWorkaroundActivity:             0.897  242/262:                  {1, 2}                                                        1:(2156.25146,716.502441,0.750000000,0.205078125)    2:[2128.26099,1181.17969,0.850000024,0.197753906]",
              "04-27 07:07:33.092  9196  9196 I MultiTouchBugWorkaroundActivity:             0.897  243/262:           MOVE   {1, 2}                                                        1:[2156.25146,716.502441,0.750000000,0.205078125]    2: 2129.26074,1179.18115,0.850000024,0.198730469",
              "04-27 07:07:33.092  9196  9196 I MultiTouchBugWorkaroundActivity:             0.904  244/262:                  {1, 2}                                                        1:(2156.25146,716.502441,0.750000000,0.204589844)    2:[2129.26074,1179.18115,0.850000024,0.198730469]",
              "04-27 07:07:33.092  9196  9196 I MultiTouchBugWorkaroundActivity:             0.904  245/262:                  {1, 2}                                                        1:[2156.25146,716.502441,0.750000000,0.204589844]    2: 2130.26050,1178.18176,0.837500036,0.201171875",
              "04-27 07:07:33.092  9196  9196 I MultiTouchBugWorkaroundActivity:             0.911  246/262:                  {1, 2}                                                        1:[2156.25146,716.502441,0.750000000,0.204589844]    2:[2130.26050,1178.18176,0.837500036,0.201171875]",
              "04-27 07:07:33.093  9196  9196 I MultiTouchBugWorkaroundActivity:             0.911  247/262:           MOVE   {1, 2}                                                        1:[2156.25146,716.502441,0.750000000,0.204589844]    2: 2132.25977,1176.18323,0.837500036,0.202148438",
              "04-27 07:07:33.093  9196  9196 I MultiTouchBugWorkaroundActivity:             0.919  248/262:           MOVE   {1, 2}                                                        1:[2156.25146,716.502441,0.750000000,0.204589844]    2: 2134.25903,1174.18457,0.837500036,0.203125000",
              "04-27 07:07:33.093  9196  9196 I MultiTouchBugWorkaroundActivity:             0.928  249/262:                  {1, 2}                                                        1:(2156.25146,716.502441,0.750000000,0.203613281)    2:[2134.25903,1174.18457,0.837500036,0.203125000]",
              "04-27 07:07:33.094  9196  9196 I MultiTouchBugWorkaroundActivity:             0.928  250/262:                  {1, 2}                                                        1:[2156.25146,716.502441,0.750000000,0.203613281]    2: 2136.25830,1173.18530,0.837500036,0.206054688",
              "04-27 07:07:33.094  9196  9196 I MultiTouchBugWorkaroundActivity:             0.936  251/262:                  {1, 2}                                                        1:(2156.25146,716.502441,0.737500012,0.204589844)    2:[2136.25830,1173.18530,0.837500036,0.206054688]",
              "04-27 07:07:33.094  9196  9196 I MultiTouchBugWorkaroundActivity:             0.936  252/262:                  {1, 2}                                                        1:[2156.25146,716.502441,0.737500012,0.204589844]    2: 2137.25806,1173.18530,0.837500036,0.206054688",
              "04-27 07:07:33.094  9196  9196 I MultiTouchBugWorkaroundActivity:             0.945  253/262:           MOVE   {1, 2}                                                        1:[2156.25146,716.502441,0.737500012,0.204589844]    2: 2138.25757,1174.18457,0.824999988,0.207031250",
              "04-27 07:07:33.094  9196  9196 I MultiTouchBugWorkaroundActivity:             0.953  254/262:                  {1, 2}                                                        1:(2156.25146,716.502441,0.737500012,0.201660156)    2:[2138.25757,1174.18457,0.824999988,0.207031250]",
              "04-27 07:07:33.095  9196  9196 I MultiTouchBugWorkaroundActivity:             0.953  255/262:                  {1, 2}                                                        1:[2156.25146,716.502441,0.737500012,0.201660156]    2: 2138.25757,1175.18384,0.824999988,0.209472656",
              "04-27 07:07:33.095  9196  9196 I MultiTouchBugWorkaroundActivity:             0.962  256/262:                  {1, 2}                                                        1: 2161.24951,718.501038,0.725000024,0.200683594     2:[2138.25757,1175.18384,0.824999988,0.209472656]",
              "04-27 07:07:33.095  9196  9196 I MultiTouchBugWorkaroundActivity:             0.962  257/262:           MOVE   {1, 2}                                                        1:[2161.24951,718.501038,0.725000024,0.200683594]    2:(2138.25757,1175.18384,0.824999988,0.210449219)",
              "04-27 07:07:33.096  9196  9196 I MultiTouchBugWorkaroundActivity:             0.970  258/262:                  {1, 2}                                                        1: 2162.24927,721.498962,0.712500036,0.199218750     2:[2138.25757,1175.18384,0.824999988,0.210449219]",
              "04-27 07:07:33.096  9196  9196 I MultiTouchBugWorkaroundActivity:             0.970  259/262:           MOVE   {1, 2}                                                        1:[2162.24927,721.498962,0.712500036,0.199218750]    2: 2139.25732,1176.18323,0.812500000,0.214355469",
              "04-27 07:07:33.096  9196  9196 I MultiTouchBugWorkaroundActivity:             0.977  260/262:    POINTER_UP(1) {1, 2}                                                        1:[2162.24927,721.498962,0.712500036,0.199218750]    2:[2139.25732,1176.18323,0.812500000,0.214355469]",
              "04-27 07:07:33.096  9196  9196 I MultiTouchBugWorkaroundActivity:             0.977  261/262:            UP(2) {2}                                                                                                              2:[2139.25732,1176.18323,0.812500000,0.214355469]",
          ""),
      };  // dumpStrings

      for (final String dumpStringIn : dumpStrings) {
        ArrayList<LogicalMotionEvent> parsed = null;
        try {
          parsed = parseDump(dumpStringIn);
        } catch (java.text.ParseException e) {
          throw new AssertionError(e);
        }
        if (parsed != null) {
          String dumpStringOut = dumpString(parsed);

          Log.i(TAG, "          parseDump succeeded!");
          Log.i(TAG, "          Here's it back out:");
          Log.i(TAG, "          ============================");
          LogMultiline(TAG, dumpStringOut);
          Log.i(TAG, "          ============================");

          // XXX I'd like to print to mTracePrintWriter, but that's not static
        }
      }

      if (verboseLevel >= 1) Log.i(TAG, "        out LogicalMotionEvent.testParseDump");
    }  // testParseDump
  }  // class LogicalMotionEvent

  public static void unitTest() {
    int verboseLevel = 1;  // reasonable to be 1 for real
    if (verboseLevel >= 1) Log.i(TAG, "    in FixedOnTouchListener.unitTest");
    LogicalMotionEvent.testParseDump();
    if (verboseLevel >= 1) Log.i(TAG, "    out FixedOnTouchListener.unitTest");
  }

  // The sequence of events that arms the fixer is two consecutive events:
  //     1. POINTER_DOWN(0) with exactly one other pointer id1 already down, followed by:
  //            anchor0x,anchor0y = the x,y of that event
  //     2. MOVE, with 0 and id1 down.
  //            anchor1x,anchor1y = the primary (non-historical) x,y of that event
  // While armed:
  //        if id 0's x,y appears to be anchor0x,anchor0y, change it to its previous value
  //        if id id1's x,y appears to be anchor1x,anchor1y, change it to its previous value
  // The pattern that disarms the fixer is either:
  //     - a new different arming
  //     - if either id0 or id1 experiences the same x,y twice in a row
  //        that is *not* the respective anchor position, then the bug isn't happening; disarm
  // Note, we do *not* disarm the fixer on:
  //     - a third pointer down (that doesn't affect the bug)
  //     - id 0 or id1 UP (the bug can still effect the other one,
  //       both on that UP event and subsequent ones.
  //       generally only for about 1/10 of a second, but who knows)
  //     - we could disarm id 0 when it goes UP, and disarm id id1 when it goes up, though.
  // UPDATE: actually the going-down id is sometimes not zero (infrequently).  We call it id0.
  // So the states are:
  private final int STATE_DISARMED = 0;
  private final int STATE_ARMING = 1;
  private final int STATE_ARMED = 2;
  private final String stateToString(int state) {
    if (state == STATE_DISARMED) return "DISARMED";
    if (state == STATE_ARMING) return "ARMING";
    if (state == STATE_ARMED) return "ARMED";
    CHECK(false);
    return null;
  }

  private int mCurrentState = STATE_DISARMED;
  private int mId0 = -1;  // gets set to the going-down pointer when arming (first event seen)
  private int mId1 = -1;  // gets set to the already-down pointer when arming (first event seen)
  private float mAnchor0x = Float.NaN;  // gets set when arming (first event seen)
  private float mAnchor0y = Float.NaN;  // gets set when arming (first event seen)
  private float mAnchor1x = Float.NaN;  // gets set when armed (second event seen)
  private float mAnchor1y = Float.NaN;  // gets set when armed (second event seen)
  private float mLastKnownGood0x = Float.NaN;  // set any time when armed and id 0 has an x,y that we didn't correct
  private float mLastKnownGood0y = Float.NaN;  // set any time when armed and id 0 has an x,y that we didn't correct
  private float mLastKnownGood1x = Float.NaN;  // set any time when armed and mId1 gets an x,y that we didn't correct
  private float mLastKnownGood1y = Float.NaN;  // set any time when armed and mId1 gets an x,y that we didn't correct

  private void moveToSTATE_DISARMED() {
    mCurrentState = STATE_DISARMED;
    mId0 = -1;
    mId1 = -1;
    mAnchor0x = Float.NaN;
    mAnchor0y = Float.NaN;
    mAnchor1x = Float.NaN;
    mAnchor1y = Float.NaN;
    mLastKnownGood0x = Float.NaN;
    mLastKnownGood0y = Float.NaN;
    mLastKnownGood1x = Float.NaN;
    mLastKnownGood1y = Float.NaN;
  }
  private void moveToSTATE_ARMING(int id0, int id1, float anchor0x, float anchor0y) {
    mCurrentState = STATE_ARMING;
    mId0 = id0;
    mId1 = id1;
    mAnchor0x = anchor0x;
    mAnchor0y = anchor0y;
    mAnchor1x = Float.NaN;
    mAnchor1y = Float.NaN;
    mLastKnownGood0x = Float.NaN;
    mLastKnownGood0y = Float.NaN;
    mLastKnownGood1x = Float.NaN;
    mLastKnownGood1y = Float.NaN;
  }
  private void moveToSTATE_ARMED(float anchor1x, float anchor1y, float lastKnownGood0x, float lastKnownGood0y, float lastKnownGood1x, float lastKnownGood1y) {
    mCurrentState = STATE_ARMED;
    CHECK_GE(mId0, 0);
    CHECK_GE(mId1, 0);
    CHECK(!Float.isNaN(mAnchor0x));
    CHECK(!Float.isNaN(mAnchor0y));
    mAnchor1x = anchor1x;
    mAnchor1y = anchor1y;
    mLastKnownGood0x = lastKnownGood0x;
    mLastKnownGood0y = lastKnownGood0y;
    mLastKnownGood1x = lastKnownGood1x;
    mLastKnownGood1y = lastKnownGood1y;
  }

  private void correctPointerCoordsUsingState(
      MotionEvent unfixed,
      int historyIndex,
      long eventTime,
      MotionEvent.PointerCoords pointerCoords[],
      ArrayList<StringBuilder> annotationsOrNull  // if not null, we append one item.
      ) {
    final int verboseLevel = 0;
    if (verboseLevel >= 1) Log.i(TAG, "        in correctPointerCoordsUsingState(historyIndex="+historyIndex+"/"+unfixed.getHistorySize()+", eventTime="+(eventTime-unfixed.getDownTime())/1000.+")  before: "+stateToString(mCurrentState));
    if (verboseLevel >= 1) Log.i(TAG, "          before: id0="+mId0+" id1="+mId1+" anchor0="+mAnchor0x+","+mAnchor0y+" anchor1="+mAnchor1x+","+mAnchor1y+" lkg0="+mLastKnownGood0x+","+mLastKnownGood0y+" lkg1="+mLastKnownGood1x+","+mLastKnownGood1y+"");
    final int action = unfixed.getActionMasked();
    final int actionIndex = unfixed.getActionIndex();
    final int pointerCount = unfixed.getPointerCount();
    final int historySize = unfixed.getHistorySize();

    // No matter what state we're in,
    // if we see the first event of the arming sequence,
    // honor it.
    if (action == MotionEvent.ACTION_POINTER_DOWN
     && pointerCount == 2) {
      final int id0 = unfixed.getPointerId(actionIndex);  // the one going down
      final int id1 = unfixed.getPointerId(1 - actionIndex);  // the one that was already down
      moveToSTATE_ARMING(id0, id1,
                         /*anchor0x=*/pointerCoords[actionIndex].x,
                         /*anchor0y=*/pointerCoords[actionIndex].y);
    } else if (mCurrentState == STATE_ARMING) {
      if (action == MotionEvent.ACTION_MOVE) {
        if (historyIndex == historySize  // i.e. this is the "primary" sub-event, not historical
         && pointerCount == 2
         && unfixed.findPointerIndex(mId0) >= 0
         && unfixed.findPointerIndex(mId1) >= 0) {
          final int index0 = unfixed.findPointerIndex(mId0);
          final int index1 = unfixed.findPointerIndex(mId1);
          moveToSTATE_ARMED(/*anchor1x=*/pointerCoords[index1].x,
                            /*anchor1y=*/pointerCoords[index1].y,
                            /*lastKnownGood0x=*/pointerCoords[index0].x,
                            /*lastKnownGood0y=*/pointerCoords[index0].y,
                            /*lastKnownGood1x=*/pointerCoords[index1].x,
                            /*lastKnownGood1y=*/pointerCoords[index1].y);
        } else {
          // We're in a historical sub-event of what may be the arming event.  Do nothing special.
        }
      } else {
        // Didn't see the second event of the arming sequence; disarm.
        moveToSTATE_DISARMED();
      }
    } else if (mCurrentState == STATE_ARMED) {  // i.e. if was already armed (not just now got armed)
      final int index0 = unfixed.findPointerIndex(mId0);
      if (index0 != -1) {
        // id0 is still down (although id1 might not be).
        if (pointerCoords[index0].x == mAnchor0x
         && pointerCoords[index0].y == mAnchor0y) {
          // Pointer 0 moved to (or stayed still at) the anchor.
          // That's what happens when it meant to stay at mLastKnownGood0.
          // Correct it.
          pointerCoords[index0].x = mLastKnownGood0x;
          pointerCoords[index0].y = mLastKnownGood0y;
        } else {
          if (pointerCoords[index0].x == mLastKnownGood0x
           && pointerCoords[index0].y == mLastKnownGood0y) {
            // Pointer id0 stayed the same, and is *not* the anchor.
            // The bug is not happening (since, when the bug is happening,
            // staying the same always gets botched into moving to the anchor).
            if (verboseLevel >= 1) Log.i(TAG, "          pointer 0 stayed the same at "+mLastKnownGood0x+","+mLastKnownGood0y+", and is *not* anchor. bug isn't happening (or isn't happening any more).");
            moveToSTATE_DISARMED();
          } else {
            mLastKnownGood0x = pointerCoords[index0].x;
            mLastKnownGood0y = pointerCoords[index0].y;
          }
        }
      }
      if (mCurrentState == STATE_ARMED) {  // still, i.e. if we didn't just disarm
        int index1 = unfixed.findPointerIndex(mId1);
        if (index1 != -1) {
          // id mId1 is still down (although id 0 might not be).
          if (pointerCoords[index1].x == mAnchor1x
           && pointerCoords[index1].y == mAnchor1y) {
            // Pointer mId1 moved to (or stayed still at) the anchor.
            // That's what happens when it meant to stay at mLastKnownGood1.
            // Correct it.
            pointerCoords[index1].x = mLastKnownGood1x;
            pointerCoords[index1].y = mLastKnownGood1y;
          } else {
            if (pointerCoords[index1].x == mLastKnownGood1x
             && pointerCoords[index1].y == mLastKnownGood1y) {
              // Pointer mId1 stayed the same, and is *not* the anchor.
              // The bug is not happening (since, when the bug is happening,
              // staying the same always gets botched into moving to the anchor).
              if (verboseLevel >= 1) Log.i(TAG, "          pointer mId1="+mId1+" stayed the same at "+mLastKnownGood1x+","+mLastKnownGood1y+", and is *not* anchor. bug isn't happening (or isn't happening any more).");
              moveToSTATE_DISARMED();
            } else {
              mLastKnownGood1x = pointerCoords[index1].x;
              mLastKnownGood1y = pointerCoords[index1].y;
            }
          }
        }
      }
    }  // STATE_ARMED

    if (verboseLevel >= 1) Log.i(TAG, "          after: id0="+mId0+" id1="+mId1+" anchor0="+mAnchor0x+","+mAnchor0y+" anchor1="+mAnchor1x+","+mAnchor1y+" lkg0="+mLastKnownGood0x+","+mLastKnownGood0y+" lkg1="+mLastKnownGood1x+","+mLastKnownGood1y+"");
    if (verboseLevel >= 1) Log.i(TAG, "        out correctPointerCoordsUsingState(historyIndex="+historyIndex+"/"+unfixed.getHistorySize()+", eventTime="+(eventTime-unfixed.getDownTime())/1000.+")  after: "+stateToString(mCurrentState));
  }  // correctPointerCoordsUsingState


  // Framework calls this; we create a fixed MotionEvent
  // and call the wrapped listener on it.
  @Override
  public boolean onTouch(View view, MotionEvent unfixed) {
    final int verboseLevel = 1;  // 0: nothing, 1: dump entire sequence on final UP, 2: and in/out
    if (verboseLevel >= 2) Log.i(TAG, "    in FixedOnTouchListener onTouch");

    LogicalMotionEvent.breakDown(unfixed, mLogicalMotionEventsSinceFirstDown);  // for post-mortem analysis

    boolean answer = false;
    {
      final int pointerCount = unfixed.getPointerCount();
      final int historySize = unfixed.getHistorySize();

      final MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[pointerCount];  // CBB: reuse
      for (int index = 0; index < pointerCount; ++index) {
        pointerProperties[index] = new MotionEvent.PointerProperties();
        unfixed.getPointerProperties(index, pointerProperties[index]);
      }

      final MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[pointerCount];  // CBB: reuse
      MotionEvent fixed = null;
      try {
        for (int h = 0; h < historySize+1; ++h) {
          for (int index = 0; index < pointerCount; ++index) {
            pointerCoords[index] = new MotionEvent.PointerCoords();
            if (h==historySize) {
              unfixed.getPointerCoords(index, pointerCoords[index]);
            } else {
              unfixed.getHistoricalPointerCoords(index, h, pointerCoords[index]);
            }
          }
          long subEventTime = h==historySize ? unfixed.getEventTime() : unfixed.getHistoricalEventTime(h);
          correctPointerCoordsUsingState(unfixed, h, subEventTime, pointerCoords, mAnnotationsOrNull);
          int historicalMetaState = unfixed.getMetaState(); // huh? this can't be right, but I don't see any way to query metaState per-history
          if (h == 0) {
            fixed = MotionEvent.obtain(
                unfixed.getDownTime(),
                subEventTime,
                unfixed.getAction(),
                pointerCount,
                pointerProperties,
                pointerCoords,
                historicalMetaState,
                unfixed.getButtonState(),
                unfixed.getXPrecision(),
                unfixed.getYPrecision(),
                unfixed.getDeviceId(),
                unfixed.getEdgeFlags(),
                unfixed.getSource(),
                unfixed.getFlags());
            // Make sure we got some of the tricky ones right...
            CHECK_EQ(fixed.getAction(), unfixed.getAction());
            CHECK_EQ(fixed.getActionMasked(), unfixed.getActionMasked());
            CHECK_EQ(fixed.getActionIndex(), unfixed.getActionIndex());
          } else {
            fixed.addBatch(subEventTime,
                           pointerCoords,
                           historicalMetaState);
          }
        }
        LogicalMotionEvent.breakDown(fixed, mFixedLogicalMotionEventsSinceFirstDown);  // for post-mortem analysis
        answer = wrapped.onTouch(view, fixed);
      } finally {
        if (fixed != null) {
          fixed.recycle();
        }
      }
    }

    if (unfixed.getActionMasked() == MotionEvent.ACTION_UP) {
      if (verboseLevel >= 1) {
        Log.i(TAG, "      ===============================================================");
        Log.i(TAG, "      UNFIXED LOGICAL MOTION EVENT SEQUENCE:");
        String unfixedString = LogicalMotionEvent.dumpString(mLogicalMotionEventsSinceFirstDown);
        LogicalMotionEvent.LogMultiline(TAG, unfixedString);
        Log.i(TAG, "      ===============================================================");
        Log.i(TAG, "      ===============================================================");
        Log.i(TAG, "      FIXED LOGICAL MOTION EVENT SEQUENCE:");
        String fixedString = LogicalMotionEvent.dumpString(mFixedLogicalMotionEventsSinceFirstDown);
        LogicalMotionEvent.LogMultiline(TAG, fixedString);
        Log.i(TAG, "      ===============================================================");

        if (mTracePrintWriterOrNull != null) {
          mTracePrintWriterOrNull.println("      ===============================================================");
          mTracePrintWriterOrNull.println("      UNFIXED LOGICAL MOTION EVENT SEQUENCE:");
          mTracePrintWriterOrNull.println(unfixedString);
          mTracePrintWriterOrNull.println("      ===============================================================");
          mTracePrintWriterOrNull.println("      ===============================================================");
          mTracePrintWriterOrNull.println("      FIXED LOGICAL MOTION EVENT SEQUENCE:");
          mTracePrintWriterOrNull.println(fixedString);
          mTracePrintWriterOrNull.println("      ===============================================================");
          mTracePrintWriterOrNull.flush();
        }
      }
      mLogicalMotionEventsSinceFirstDown.clear();
      mFixedLogicalMotionEventsSinceFirstDown.clear();
    }

    if (verboseLevel >= 2) Log.i(TAG, "    out FixedOnTouchListener onTouch, returning "+answer);
    return answer;
  }  // onTouch
}


/*

BUG: two things got through here with participants {1,2}, I think?  Not quite sure yet.   Can I replay??


04-27 07:46:53.810 12610 12610 I MultiTouchBugWorkaroundActivity:             1.575  537/865:           MOVE   {2}                                                                                                                         2: 1529.96887,1088.24414,0.750000000,0.206542969
04-27 07:46:53.810 12610 12610 I MultiTouchBugWorkaroundActivity:             1.579  538/865:           MOVE   {2}                                                                                                                         2: 1523.47107,1088.24426,0.762499988,0.221191406
04-27 07:46:53.810 12610 12610 I MultiTouchBugWorkaroundActivity:             1.589  539/865:  POINTER_DOWN(0) {0, 2}              0: 1960.31934,284.802216,1.06250000,0.225585938 A!                                                      2:[1523.47107,1088.24426,0.762499988,0.221191406]
04-27 07:46:53.810 12610 12610 I MultiTouchBugWorkaroundActivity:             1.589  540/865:                  {0, 2}              0:[1960.31934,284.802216,1.06250000,0.225585938]A                                                       2: 1508.47632,1091.24219,0.787500024,0.223144531
04-27 07:46:53.811 12610 12610 I MultiTouchBugWorkaroundActivity:             1.593  541/865:           MOVE   {0, 2}              0:[1960.31934,284.802216,1.06250000,0.225585938]A                                                       2: 1500.97900,1092.74121,0.787500024,0.223144531 B!
04-27 07:46:53.811 12610 12610 I MultiTouchBugWorkaroundActivity:             1.598  542/865:                  {0, 2}              0:(1960.31934,284.802216,1.07500005,0.224609375)A                                                       2:[1500.97900,1092.74121,0.787500024,0.223144531]B?
04-27 07:46:53.811 12610 12610 I MultiTouchBugWorkaroundActivity:             1.598  543/865:           MOVE   {0, 2}              0:[1960.31934,284.802216,1.07500005,0.224609375]A                                                       2: 1495.48071,1093.24084,0.800000012,0.223144531
04-27 07:46:53.811 12610 12610 I MultiTouchBugWorkaroundActivity:             1.609  544/865:  POINTER_DOWN(1) {0, 1, 2}           0:[1960.31934,284.802216,1.07500005,0.224609375]A   1: 1576.45264,611.575256,1.33749998,0.246582031     2:[1495.48071,1093.24084,0.800000012,0.223144531]
04-27 07:46:53.812 12610 12610 I MultiTouchBugWorkaroundActivity:             1.609  545/865:                  {0, 1, 2}           0:(1960.31934,284.802216,1.06250000,0.225585938)A   1:[1576.45264,611.575256,1.33749998,0.246582031]    2: 1500.97900,1092.74121,0.800000012,0.223144531 B?
04-27 07:46:53.812 12610 12610 I MultiTouchBugWorkaroundActivity:             1.609  546/865:           MOVE   {0, 1, 2}           0:[1960.31934,284.802216,1.06250000,0.225585938]A   1:[1576.45264,611.575256,1.33749998,0.246582031]    2: 1484.48462,1096.23865,0.812500000,0.223144531
04-27 07:46:53.812 12610 12610 I MultiTouchBugWorkaroundActivity:             1.619  547/865:                  {0, 1, 2}           0:(1960.31934,284.802216,1.06250000,0.224609375)A   1:[1576.45264,611.575256,1.33749998,0.246582031]    2: 1500.97900,1092.74121,0.812500000,0.223144531 B?
04-27 07:46:53.812 12610 12610 I MultiTouchBugWorkaroundActivity:             1.619  548/865:           MOVE   {0, 1, 2}           0:[1960.31934,284.802216,1.06250000,0.224609375]A   1:[1576.45264,611.575256,1.33749998,0.246582031]    2: 1476.48743,1100.23596,0.837500036,0.223144531
04-27 07:46:53.812 12610 12610 I MultiTouchBugWorkaroundActivity:             1.630  549/865:                  {0, 1, 2}           0:(1960.31934,284.802216,1.03750002,0.223144531)A   1:[1576.45264,611.575256,1.33749998,0.246582031]    2: 1500.97900,1092.74121,0.837500036,0.223144531 B?
04-27 07:46:53.813 12610 12610 I MultiTouchBugWorkaroundActivity:             1.630  550/865:           MOVE   {0, 1, 2}           0:[1960.31934,284.802216,1.03750002,0.223144531]A   1:[1576.45264,611.575256,1.33749998,0.246582031]    2: 1473.48840,1104.23315,0.837500036,0.224121094
04-27 07:46:53.813 12610 12610 I MultiTouchBugWorkaroundActivity:             1.630  551/865:  POINTER_DOWN(3) {0, 1, 2, 3}        0:[1960.31934,284.802216,1.03750002,0.223144531]A   1:[1576.45264,611.575256,1.33749998,0.246582031]    2:[1473.48840,1104.23315,0.837500036,0.224121094]    3: 392.863586,651.547546,0.837500036,0.194824219
04-27 07:46:53.813 12610 12610 I MultiTouchBugWorkaroundActivity:             1.642  552/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,1.03750002,0.223144531]A   1:(1576.45264,611.575256,1.32500005,0.245605469)    2: 1500.97900,1092.74121,0.837500036,0.224121094 B?  3:[392.863586,651.547546,0.837500036,0.194824219]
04-27 07:46:53.814 12610 12610 I MultiTouchBugWorkaroundActivity:             1.642  553/865:                  {0, 1, 2, 3}        0:(1960.31934,284.802216,1.02499998,0.221191406)A   1:[1576.45264,611.575256,1.32500005,0.245605469]    2:[1500.97900,1092.74121,0.837500036,0.224121094]B?  3:[392.863586,651.547546,0.837500036,0.194824219]
04-27 07:46:53.814 12610 12610 I MultiTouchBugWorkaroundActivity:             1.642  554/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,1.02499998,0.221191406]A   1:[1576.45264,611.575256,1.32500005,0.245605469]    2: 1472.48877,1109.22974,0.850000024,0.221191406     3:[392.863586,651.547546,0.837500036,0.194824219]
04-27 07:46:53.814 12610 12610 I MultiTouchBugWorkaroundActivity:             1.642  555/865:           MOVE   {0, 1, 2, 3}        0:[1960.31934,284.802216,1.02499998,0.221191406]A   1:[1576.45264,611.575256,1.32500005,0.245605469]    2: 1500.97900,1092.74121,0.850000024,0.221191406 B?  3:(392.863586,651.547546,0.850000024,0.196777344)
04-27 07:46:53.815 12610 12610 I MultiTouchBugWorkaroundActivity:             1.654  556/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,1.02499998,0.221191406]A   1:(1576.45264,611.575256,1.31250000,0.244628906)    2:[1500.97900,1092.74121,0.850000024,0.221191406]B?  3:[392.863586,651.547546,0.850000024,0.196777344]
04-27 07:46:53.815 12610 12610 I MultiTouchBugWorkaroundActivity:             1.654  557/865:                  {0, 1, 2, 3}        0: 1953.32178,286.800842,1.00000000,0.219238281     1:[1576.45264,611.575256,1.31250000,0.244628906]    2:[1500.97900,1092.74121,0.850000024,0.221191406]B?  3:[392.863586,651.547546,0.850000024,0.196777344]
04-27 07:46:53.815 12610 12610 I MultiTouchBugWorkaroundActivity:             1.654  558/865:           MOVE   {0, 1, 2, 3}        0: 1960.31934,284.802216,1.00000000,0.219238281 A?  1:[1576.45264,611.575256,1.31250000,0.244628906]    2: 1473.48840,1117.22412,0.862500012,0.221191406     3:[392.863586,651.547546,0.850000024,0.196777344]
04-27 07:46:53.816 12610 12610 I MultiTouchBugWorkaroundActivity:             1.667  559/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,1.00000000,0.219238281]A?  1:(1576.45264,611.575256,1.30000007,0.244140625)    2: 1500.97900,1092.74121,0.862500012,0.221191406 B?  3:[392.863586,651.547546,0.850000024,0.196777344]
04-27 07:46:53.816 12610 12610 I MultiTouchBugWorkaroundActivity:             1.667  560/865:                  {0, 1, 2, 3}        0: 1945.32458,290.798065,0.975000024,0.217285156     1:[1576.45264,611.575256,1.30000007,0.244140625]    2:[1500.97900,1092.74121,0.862500012,0.221191406]B?  3:[392.863586,651.547546,0.850000024,0.196777344]
04-27 07:46:53.816 12610 12610 I MultiTouchBugWorkaroundActivity:             1.667  561/865:                  {0, 1, 2, 3}        0: 1960.31934,284.802216,0.975000024,0.217285156 A?  1:[1576.45264,611.575256,1.30000007,0.244140625]    2: 1475.48767,1129.21582,0.862500012,0.221191406     3:[392.863586,651.547546,0.850000024,0.196777344]
04-27 07:46:53.816 12610 12610 I MultiTouchBugWorkaroundActivity:             1.667  562/865:           MOVE   {0, 1, 2, 3}        0:[1960.31934,284.802216,0.975000024,0.217285156]A?  1:[1576.45264,611.575256,1.30000007,0.244140625]    2: 1500.97900,1092.74121,0.862500012,0.221191406 B?  3:(392.863586,651.547546,0.837500036,0.193359375)
04-27 07:46:53.817 12610 12610 I MultiTouchBugWorkaroundActivity:             1.679  563/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,0.975000024,0.217285156]A?  1:(1576.45264,611.575256,1.27499998,0.242187500)    2:[1500.97900,1092.74121,0.862500012,0.221191406]B?  3:[392.863586,651.547546,0.837500036,0.193359375]
04-27 07:46:53.817 12610 12610 I MultiTouchBugWorkaroundActivity:             1.679  564/865:                  {0, 1, 2, 3}        0: 1941.32593,297.793182,0.949999988,0.217285156     1:[1576.45264,611.575256,1.27499998,0.242187500]    2:[1500.97900,1092.74121,0.862500012,0.221191406]B?  3:[392.863586,651.547546,0.837500036,0.193359375]
04-27 07:46:53.817 12610 12610 I MultiTouchBugWorkaroundActivity:             1.679  565/865:                  {0, 1, 2, 3}        0: 1960.31934,284.802216,0.949999988,0.217285156 A?  1:[1576.45264,611.575256,1.27499998,0.242187500]    2: 1481.48560,1143.20605,0.875000000,0.221191406     3:[392.863586,651.547546,0.837500036,0.193359375]
04-27 07:46:53.818 12610 12610 I MultiTouchBugWorkaroundActivity:             1.679  566/865:           MOVE   {0, 1, 2, 3}        0:[1960.31934,284.802216,0.949999988,0.217285156]A?  1:[1576.45264,611.575256,1.27499998,0.242187500]    2: 1500.97900,1092.74121,0.875000000,0.221191406 B?  3:(392.863586,651.547546,0.812500000,0.192382813)
04-27 07:46:53.818 12610 12610 I MultiTouchBugWorkaroundActivity:             1.679  567/865:  POINTER_DOWN(4) {0, 1, 2, 3, 4}     0:[1960.31934,284.802216,0.949999988,0.217285156]A?  1:[1576.45264,611.575256,1.27499998,0.242187500]    2:[1500.97900,1092.74121,0.875000000,0.221191406]B?  3:[392.863586,651.547546,0.812500000,0.192382813]    4: 823.713989,859.403198,1.07500005,0.223144531
04-27 07:46:53.819 12610 12610 I MultiTouchBugWorkaroundActivity:             1.691  568/865:                  {0, 1, 2, 3, 4}     0:[1960.31934,284.802216,0.949999988,0.217285156]A?  1: 1577.45227,615.572510,1.26250005,0.240234375     2:[1500.97900,1092.74121,0.875000000,0.221191406]B?  3:[392.863586,651.547546,0.812500000,0.192382813]    4:[823.713989,859.403198,1.07500005,0.223144531]
04-27 07:46:53.819 12610 12610 I MultiTouchBugWorkaroundActivity:             1.691  569/865:                  {0, 1, 2, 3, 4}     0: 1938.32703,310.784180,0.937500000,0.216308594     1:[1577.45227,615.572510,1.26250005,0.240234375]    2:[1500.97900,1092.74121,0.875000000,0.221191406]B?  3:[392.863586,651.547546,0.812500000,0.192382813]    4:[823.713989,859.403198,1.07500005,0.223144531]
04-27 07:46:53.819 12610 12610 I MultiTouchBugWorkaroundActivity:             1.691  570/865:           MOVE   {0, 1, 2, 3, 4}     0: 1960.31934,284.802216,0.937500000,0.216308594 A?  1:[1577.45227,615.572510,1.26250005,0.240234375]    2: 1489.48291,1156.19702,0.875000000,0.220214844     3:[392.863586,651.547546,0.812500000,0.192382813]    4:[823.713989,859.403198,1.07500005,0.223144531]
04-27 07:46:53.820 12610 12610 I MultiTouchBugWorkaroundActivity:             1.691  571/865:    POINTER_UP(3) {0, 1, 2, 3, 4}     0:[1960.31934,284.802216,0.937500000,0.216308594]A?  1:[1577.45227,615.572510,1.26250005,0.240234375]    2:[1489.48291,1156.19702,0.875000000,0.220214844]    3:[392.863586,651.547546,0.812500000,0.192382813]    4:[823.713989,859.403198,1.07500005,0.223144531]
04-27 07:46:53.820 12610 12610 I MultiTouchBugWorkaroundActivity:             1.691  572/865:           MOVE   {0, 1, 2, 4}        0:[1960.31934,284.802216,0.937500000,0.216308594]A?  1:[1577.45227,615.572510,1.26250005,0.240234375]    2: 1500.97900,1092.74121,0.875000000,0.220214844 B?                                                      4:(823.713989,859.403198,1.06250000,0.221191406)
04-27 07:46:53.820 12610 12610 I MultiTouchBugWorkaroundActivity:             1.702  573/865:                  {0, 1, 2, 4}        0:[1960.31934,284.802216,0.937500000,0.216308594]A?  1: 1577.45227,630.562073,1.25000000,0.238769531     2:[1500.97900,1092.74121,0.875000000,0.220214844]B?                                                      4:[823.713989,859.403198,1.06250000,0.221191406]
04-27 07:46:53.821 12610 12610 I MultiTouchBugWorkaroundActivity:             1.703  574/865:                  {0, 1, 2, 4}        0: 1937.32739,332.768921,0.937500000,0.215332031     1:[1577.45227,630.562073,1.25000000,0.238769531]    2:[1500.97900,1092.74121,0.875000000,0.220214844]B?                                                      4:[823.713989,859.403198,1.06250000,0.221191406]
04-27 07:46:53.821 12610 12610 I MultiTouchBugWorkaroundActivity:             1.703  575/865:                  {0, 1, 2, 4}        0: 1960.31934,284.802216,0.937500000,0.215332031 A?  1:[1577.45227,630.562073,1.25000000,0.238769531]    2: 1503.47803,1171.18665,0.875000000,0.219238281                                                         4:[823.713989,859.403198,1.06250000,0.221191406]
04-27 07:46:53.821 12610 12610 I MultiTouchBugWorkaroundActivity:             1.703  576/865:           MOVE   {0, 1, 2, 4}        0:[1960.31934,284.802216,0.937500000,0.215332031]A?  1:[1577.45227,630.562073,1.25000000,0.238769531]    2: 1500.97900,1092.74121,0.875000000,0.219238281 B?                                                      4:(823.713989,859.403198,1.05000007,0.221191406)
04-27 07:46:53.822 12610 12610 I MultiTouchBugWorkaroundActivity:             1.703  577/865:  POINTER_DOWN(3) {0, 1, 2, 3, 4}     0:[1960.31934,284.802216,0.937500000,0.215332031]A?  1:[1577.45227,630.562073,1.25000000,0.238769531]    2:[1500.97900,1092.74121,0.875000000,0.219238281]B?  3: 1062.63110,1315.08667,0.987500012,0.204589844     4:[823.713989,859.403198,1.05000007,0.221191406]
04-27 07:46:53.822 12610 12610 I MultiTouchBugWorkaroundActivity:             1.713  578/865:                  {0, 1, 2, 3, 4}     0:[1960.31934,284.802216,0.937500000,0.215332031]A?  1: 1577.45227,652.546814,1.23750007,0.237792969     2:[1500.97900,1092.74121,0.875000000,0.219238281]B?  3:[1062.63110,1315.08667,0.987500012,0.204589844]    4:[823.713989,859.403198,1.05000007,0.221191406]
04-27 07:46:53.822 12610 12610 I MultiTouchBugWorkaroundActivity:             1.713  579/865:                  {0, 1, 2, 3, 4}     0: 1938.32703,358.750854,0.937500000,0.216308594     1:[1577.45227,652.546814,1.23750007,0.237792969]    2:[1500.97900,1092.74121,0.875000000,0.219238281]B?  3:[1062.63110,1315.08667,0.987500012,0.204589844]    4:[823.713989,859.403198,1.05000007,0.221191406]
04-27 07:46:53.823 12610 12610 I MultiTouchBugWorkaroundActivity:             1.713  580/865:                  {0, 1, 2, 3, 4}     0: 1960.31934,284.802216,0.937500000,0.216308594 A?  1:[1577.45227,652.546814,1.23750007,0.237792969]    2: 1515.47388,1187.17554,0.875000000,0.219238281     3:[1062.63110,1315.08667,0.987500012,0.204589844]    4:[823.713989,859.403198,1.05000007,0.221191406]
04-27 07:46:53.823 12610 12610 I MultiTouchBugWorkaroundActivity:             1.713  581/865:                  {0, 1, 2, 3, 4}     0:[1960.31934,284.802216,0.937500000,0.216308594]A?  1:[1577.45227,652.546814,1.23750007,0.237792969]    2: 1500.97900,1092.74121,0.875000000,0.219238281 B?  3:[1062.63110,1315.08667,0.987500012,0.204589844]    4:(823.713989,859.403198,1.01250005,0.220214844)
04-27 07:46:53.824 12610 12610 I MultiTouchBugWorkaroundActivity:             1.713  582/865:                  {0, 1, 2, 3, 4}     0:[1960.31934,284.802216,0.937500000,0.216308594]A?  1:[1577.45227,652.546814,1.23750007,0.237792969]    2:[1500.97900,1092.74121,0.875000000,0.219238281]B?  3:(1062.63110,1315.08667,0.987500012,0.205566406)    4:[823.713989,859.403198,1.01250005,0.220214844]
04-27 07:46:53.824 12610 12610 I MultiTouchBugWorkaroundActivity:             1.724  583/865:                  {0, 1, 2, 3, 4}     0:[1960.31934,284.802216,0.937500000,0.216308594]A?  1: 1578.45190,681.526733,1.23750007,0.237792969     2:[1500.97900,1092.74121,0.875000000,0.219238281]B?  3:[1062.63110,1315.08667,0.987500012,0.205566406]    4:[823.713989,859.403198,1.01250005,0.220214844]
04-27 07:46:53.824 12610 12610 I MultiTouchBugWorkaroundActivity:             1.724  584/865:                  {0, 1, 2, 3, 4}     0: 1941.32593,388.730042,0.949999988,0.216308594     1:[1578.45190,681.526733,1.23750007,0.237792969]    2:[1500.97900,1092.74121,0.875000000,0.219238281]B?  3:[1062.63110,1315.08667,0.987500012,0.205566406]    4:[823.713989,859.403198,1.01250005,0.220214844]
04-27 07:46:53.825 12610 12610 I MultiTouchBugWorkaroundActivity:             1.724  585/865:           MOVE   {0, 1, 2, 3, 4}     0: 1960.31934,284.802216,0.949999988,0.216308594 A?  1:[1578.45190,681.526733,1.23750007,0.237792969]    2: 1525.47034,1207.16162,0.875000000,0.220214844     3:[1062.63110,1315.08667,0.987500012,0.205566406]    4:[823.713989,859.403198,1.01250005,0.220214844]
04-27 07:46:53.825 12610 12610 I MultiTouchBugWorkaroundActivity:             1.724  586/865:    POINTER_UP(4) {0, 1, 2, 3, 4}     0:[1960.31934,284.802216,0.949999988,0.216308594]A?  1:[1578.45190,681.526733,1.23750007,0.237792969]    2:[1525.47034,1207.16162,0.875000000,0.220214844]    3:[1062.63110,1315.08667,0.987500012,0.205566406]    4:[823.713989,859.403198,1.01250005,0.220214844]
04-27 07:46:53.825 12610 12610 I MultiTouchBugWorkaroundActivity:             1.724  587/865:           MOVE   {0, 1, 2, 3}        0:[1960.31934,284.802216,0.949999988,0.216308594]A?  1:[1578.45190,681.526733,1.23750007,0.237792969]    2: 1500.97900,1092.74121,0.875000000,0.220214844 B?  3:(1062.63110,1315.08667,1.00000000,0.208496094)
04-27 07:46:53.826 12610 12610 I MultiTouchBugWorkaroundActivity:             1.734  588/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,0.949999988,0.216308594]A?  1: 1580.45129,714.503784,1.23750007,0.237792969     2:[1500.97900,1092.74121,0.875000000,0.220214844]B?  3:[1062.63110,1315.08667,1.00000000,0.208496094]
04-27 07:46:53.826 12610 12610 I MultiTouchBugWorkaroundActivity:             1.734  589/865:                  {0, 1, 2, 3}        0: 1946.32422,413.712708,0.962500036,0.217285156     1:[1580.45129,714.503784,1.23750007,0.237792969]    2:[1500.97900,1092.74121,0.875000000,0.220214844]B?  3:[1062.63110,1315.08667,1.00000000,0.208496094]
04-27 07:46:53.826 12610 12610 I MultiTouchBugWorkaroundActivity:             1.734  590/865:           MOVE   {0, 1, 2, 3}        0: 1960.31934,284.802216,0.962500036,0.217285156 A?  1:[1580.45129,714.503784,1.23750007,0.237792969]    2: 1533.46753,1228.14709,0.875000000,0.219238281     3:[1062.63110,1315.08667,1.00000000,0.208496094]
04-27 07:46:53.827 12610 12610 I MultiTouchBugWorkaroundActivity:             1.734  591/865:           MOVE   {0, 1, 2, 3}        0:[1960.31934,284.802216,0.962500036,0.217285156]A?  1:[1580.45129,714.503784,1.23750007,0.237792969]    2: 1500.97900,1092.74121,0.875000000,0.219238281 B?  3:(1062.63110,1315.08667,1.00000000,0.208984375)
04-27 07:46:53.827 12610 12610 I MultiTouchBugWorkaroundActivity:             1.744  592/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,0.962500036,0.217285156]A?  1: 1583.45020,745.482300,1.23750007,0.237792969     2:[1500.97900,1092.74121,0.875000000,0.219238281]B?  3:[1062.63110,1315.08667,1.00000000,0.208984375]
04-27 07:46:53.827 12610 12610 I MultiTouchBugWorkaroundActivity:             1.744  593/865:                  {0, 1, 2, 3}        0: 1955.32117,432.699493,0.975000024,0.219238281     1:[1583.45020,745.482300,1.23750007,0.237792969]    2:[1500.97900,1092.74121,0.875000000,0.219238281]B?  3:[1062.63110,1315.08667,1.00000000,0.208984375]
04-27 07:46:53.828 12610 12610 I MultiTouchBugWorkaroundActivity:             1.744  594/865:                  {0, 1, 2, 3}        0: 1960.31934,284.802216,0.975000024,0.219238281 A?  1:[1583.45020,745.482300,1.23750007,0.237792969]    2: 1540.46521,1250.13184,0.875000000,0.219238281     3:[1062.63110,1315.08667,1.00000000,0.208984375]
04-27 07:46:53.828 12610 12610 I MultiTouchBugWorkaroundActivity:             1.744  595/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,0.975000024,0.219238281]A?  1:[1583.45020,745.482300,1.23750007,0.237792969]    2: 1500.97900,1092.74121,0.875000000,0.219238281 B?  3:(1062.63110,1315.08667,1.00000000,0.209960938)
04-27 07:46:53.828 12610 12610 I MultiTouchBugWorkaroundActivity:             1.754  596/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,0.975000024,0.219238281]A?  1: 1589.44812,770.464966,1.23750007,0.237792969     2:[1500.97900,1092.74121,0.875000000,0.219238281]B?  3:[1062.63110,1315.08667,1.00000000,0.209960938]
04-27 07:46:53.828 12610 12610 I MultiTouchBugWorkaroundActivity:             1.755  597/865:                  {0, 1, 2, 3}        0: 1967.31689,442.692566,0.987500012,0.221191406     1:[1589.44812,770.464966,1.23750007,0.237792969]    2:[1500.97900,1092.74121,0.875000000,0.219238281]B?  3:[1062.63110,1315.08667,1.00000000,0.209960938]
04-27 07:46:53.829 12610 12610 I MultiTouchBugWorkaroundActivity:             1.755  598/865:                  {0, 1, 2, 3}        0: 1960.31934,284.802216,0.987500012,0.221191406 A?  1:[1589.44812,770.464966,1.23750007,0.237792969]    2: 1546.46301,1268.11938,0.875000000,0.217285156     3:[1062.63110,1315.08667,1.00000000,0.209960938]
04-27 07:46:53.829 12610 12610 I MultiTouchBugWorkaroundActivity:             1.755  599/865:           MOVE   {0, 1, 2, 3}        0:[1960.31934,284.802216,0.987500012,0.221191406]A?  1:[1589.44812,770.464966,1.23750007,0.237792969]    2: 1500.97900,1092.74121,0.875000000,0.217285156 B?  3:(1062.63110,1315.08667,1.01250005,0.210937500)
04-27 07:46:53.830 12610 12610 I MultiTouchBugWorkaroundActivity:             1.764  600/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,0.987500012,0.221191406]A?  1: 1597.44531,787.453125,1.23750007,0.237792969     2:[1500.97900,1092.74121,0.875000000,0.217285156]B?  3:[1062.63110,1315.08667,1.01250005,0.210937500]
04-27 07:46:53.831 12610 12610 I MultiTouchBugWorkaroundActivity:             1.764  601/865:                  {0, 1, 2, 3}        0: 1983.31140,444.691193,1.01250005,0.221191406     1:[1597.44531,787.453125,1.23750007,0.237792969]    2:[1500.97900,1092.74121,0.875000000,0.217285156]B?  3:[1062.63110,1315.08667,1.01250005,0.210937500]
04-27 07:46:53.831 12610 12610 I MultiTouchBugWorkaroundActivity:             1.764  602/865:                  {0, 1, 2, 3}        0: 1960.31934,284.802216,1.01250005,0.221191406 A?  1:[1597.44531,787.453125,1.23750007,0.237792969]    2: 1552.46094,1283.10889,0.887499988,0.218261719     3:[1062.63110,1315.08667,1.01250005,0.210937500]
04-27 07:46:53.832 12610 12610 I MultiTouchBugWorkaroundActivity:             1.764  603/865:           MOVE   {0, 1, 2, 3}        0:[1960.31934,284.802216,1.01250005,0.221191406]A?  1:[1597.44531,787.453125,1.23750007,0.237792969]    2: 1500.97900,1092.74121,0.887499988,0.218261719 B?  3:(1062.63110,1315.08667,1.01250005,0.213867188)
04-27 07:46:53.832 12610 12610 I MultiTouchBugWorkaroundActivity:             1.774  604/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,1.01250005,0.221191406]A?  1: 1608.44153,797.446228,1.23750007,0.238769531     2:[1500.97900,1092.74121,0.887499988,0.218261719]B?  3:[1062.63110,1315.08667,1.01250005,0.213867188]
04-27 07:46:53.832 12610 12610 I MultiTouchBugWorkaroundActivity:             1.774  605/865:                  {0, 1, 2, 3}        0: 1997.30652,440.693970,1.03750002,0.223632813     1:[1608.44153,797.446228,1.23750007,0.238769531]    2:[1500.97900,1092.74121,0.887499988,0.218261719]B?  3:[1062.63110,1315.08667,1.01250005,0.213867188]
04-27 07:46:53.833 12610 12610 I MultiTouchBugWorkaroundActivity:             1.774  606/865:                  {0, 1, 2, 3}        0: 1960.31934,284.802216,1.03750002,0.223632813 A?  1:[1608.44153,797.446228,1.23750007,0.238769531]    2: 1558.45886,1292.10266,0.887499988,0.218261719     3:[1062.63110,1315.08667,1.01250005,0.213867188]
04-27 07:46:53.833 12610 12610 I MultiTouchBugWorkaroundActivity:             1.774  607/865:           MOVE   {0, 1, 2, 3}        0:[1960.31934,284.802216,1.03750002,0.223632813]A?  1:[1608.44153,797.446228,1.23750007,0.238769531]    2: 1500.97900,1092.74121,0.887499988,0.218261719 B?  3: 1058.63245,1317.08533,1.02499998,0.214843750
04-27 07:46:53.834 12610 12610 I MultiTouchBugWorkaroundActivity:             1.785  608/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,1.03750002,0.223632813]A?  1: 1619.43774,801.443420,1.23750007,0.238769531     2:[1500.97900,1092.74121,0.887499988,0.218261719]B?  3:[1058.63245,1317.08533,1.02499998,0.214843750]
04-27 07:46:53.834 12610 12610 I MultiTouchBugWorkaroundActivity:             1.785  609/865:                  {0, 1, 2, 3}        0: 2013.30103,432.699493,1.05000007,0.225585938     1:[1619.43774,801.443420,1.23750007,0.238769531]    2:[1500.97900,1092.74121,0.887499988,0.218261719]B?  3:[1058.63245,1317.08533,1.02499998,0.214843750]
04-27 07:46:53.834 12610 12610 I MultiTouchBugWorkaroundActivity:             1.785  610/865:                  {0, 1, 2, 3}        0: 1960.31934,284.802216,1.05000007,0.225585938 A?  1:[1619.43774,801.443420,1.23750007,0.238769531]    2: 1562.45752,1298.09851,0.900000036,0.217285156     3:[1058.63245,1317.08533,1.02499998,0.214843750]
04-27 07:46:53.835 12610 12610 I MultiTouchBugWorkaroundActivity:             1.785  611/865:           MOVE   {0, 1, 2, 3}        0:[1960.31934,284.802216,1.05000007,0.225585938]A?  1:[1619.43774,801.443420,1.23750007,0.238769531]    2: 1500.97900,1092.74121,0.900000036,0.217285156 B?  3: 1051.63489,1319.08398,1.02499998,0.214843750
04-27 07:46:53.835 12610 12610 I MultiTouchBugWorkaroundActivity:             1.796  612/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,1.05000007,0.225585938]A?  1: 1630.43396,802.442749,1.25000000,0.239746094     2:[1500.97900,1092.74121,0.900000036,0.217285156]B?  3:[1051.63489,1319.08398,1.02499998,0.214843750]
04-27 07:46:53.837 12610 12610 I MultiTouchBugWorkaroundActivity:             1.796  613/865:                  {0, 1, 2, 3}        0: 2028.29578,419.708527,1.06250000,0.227539063     1:[1630.43396,802.442749,1.25000000,0.239746094]    2:[1500.97900,1092.74121,0.900000036,0.217285156]B?  3:[1051.63489,1319.08398,1.02499998,0.214843750]
04-27 07:46:53.837 12610 12610 I MultiTouchBugWorkaroundActivity:             1.796  614/865:                  {0, 1, 2, 3}        0: 1960.31934,284.802216,1.06250000,0.227539063 A?  1:[1630.43396,802.442749,1.25000000,0.239746094]    2: 1567.45581,1299.09778,0.912500024,0.218261719     3:[1051.63489,1319.08398,1.02499998,0.214843750]
04-27 07:46:53.838 12610 12610 I MultiTouchBugWorkaroundActivity:             1.796  615/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,1.06250000,0.227539063]A?  1:[1630.43396,802.442749,1.25000000,0.239746094]    2: 1500.97900,1092.74121,0.912500024,0.218261719 B?  3: 1047.63623,1320.08325,1.00000000,0.211914063
04-27 07:46:53.838 12610 12610 I MultiTouchBugWorkaroundActivity:             1.808  616/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,1.06250000,0.227539063]A?  1: 1640.43042,798.445496,1.25000000,0.240234375     2:[1500.97900,1092.74121,0.912500024,0.218261719]B?  3:[1047.63623,1320.08325,1.00000000,0.211914063]
04-27 07:46:53.839 12610 12610 I MultiTouchBugWorkaroundActivity:             1.808  617/865:                  {0, 1, 2, 3}        0: 2046.28955,400.721710,1.08749998,0.227539063     1:[1640.43042,798.445496,1.25000000,0.240234375]    2:[1500.97900,1092.74121,0.912500024,0.218261719]B?  3:[1047.63623,1320.08325,1.00000000,0.211914063]
04-27 07:46:53.839 12610 12610 I MultiTouchBugWorkaroundActivity:             1.808  618/865:                  {0, 1, 2, 3}        0: 1960.31934,284.802216,1.08749998,0.227539063 A?  1:[1640.43042,798.445496,1.25000000,0.240234375]    2: 1573.45374,1294.10132,0.925000012,0.220214844     3:[1047.63623,1320.08325,1.00000000,0.211914063]
04-27 07:46:53.839 12610 12610 I MultiTouchBugWorkaroundActivity:             1.808  619/865:           MOVE   {0, 1, 2, 3}        0:[1960.31934,284.802216,1.08749998,0.227539063]A?  1:[1640.43042,798.445496,1.25000000,0.240234375]    2: 1500.97900,1092.74121,0.925000012,0.220214844 B?  3: 1044.63733,1322.08191,0.962500036,0.208984375
04-27 07:46:53.840 12610 12610 I MultiTouchBugWorkaroundActivity:             1.818  620/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,1.08749998,0.227539063]A?  1: 1648.42761,790.451050,1.23750007,0.239746094     2:[1500.97900,1092.74121,0.925000012,0.220214844]B?  3:[1044.63733,1322.08191,0.962500036,0.208984375]
04-27 07:46:53.840 12610 12610 I MultiTouchBugWorkaroundActivity:             1.818  621/865:                  {0, 1, 2, 3}        0: 2062.28394,377.737671,1.10000002,0.229003906     1:[1648.42761,790.451050,1.23750007,0.239746094]    2:[1500.97900,1092.74121,0.925000012,0.220214844]B?  3:[1044.63733,1322.08191,0.962500036,0.208984375]
04-27 07:46:53.840 12610 12610 I MultiTouchBugWorkaroundActivity:             1.818  622/865:           MOVE   {0, 1, 2, 3}        0: 1960.31934,284.802216,1.10000002,0.229003906 A?  1:[1648.42761,790.451050,1.23750007,0.239746094]    2: 1580.45129,1284.10828,0.937500000,0.222167969     3:[1044.63733,1322.08191,0.962500036,0.208984375]
04-27 07:46:53.841 12610 12610 I MultiTouchBugWorkaroundActivity:             1.818  623/865:    POINTER_UP(3) {0, 1, 2, 3}        0:[1960.31934,284.802216,1.10000002,0.229003906]A?  1:[1648.42761,790.451050,1.23750007,0.239746094]    2:[1580.45129,1284.10828,0.937500000,0.222167969]    3:[1044.63733,1322.08191,0.962500036,0.208984375]
04-27 07:46:53.841 12610 12610 I MultiTouchBugWorkaroundActivity:             1.830  624/865:                  {0, 1, 2}           0:[1960.31934,284.802216,1.10000002,0.229003906]A?  1: 1656.42493,773.462891,1.21249998,0.236816406     2: 1500.97900,1092.74121,0.937500000,0.222167969 B?
04-27 07:46:53.841 12610 12610 I MultiTouchBugWorkaroundActivity:             1.830  625/865:                  {0, 1, 2}           0: 2079.27808,346.759186,1.11250007,0.229980469     1:[1656.42493,773.462891,1.21249998,0.236816406]    2:[1500.97900,1092.74121,0.937500000,0.222167969]B?
04-27 07:46:53.841 12610 12610 I MultiTouchBugWorkaroundActivity:             1.830  626/865:           MOVE   {0, 1, 2}           0: 1960.31934,284.802216,1.11250007,0.229980469 A?  1:[1656.42493,773.462891,1.21249998,0.236816406]    2: 1588.44849,1260.12488,0.949999988,0.222167969
04-27 07:46:53.842 12610 12610 I MultiTouchBugWorkaroundActivity:             1.830  627/865:  POINTER_DOWN(3) {0, 1, 2, 3}        0:[1960.31934,284.802216,1.11250007,0.229980469]A?  1:[1656.42493,773.462891,1.21249998,0.236816406]    2:[1588.44849,1260.12488,0.949999988,0.222167969]    3: 371.870880,642.553772,0.687500000,0.187988281
04-27 07:46:53.842 12610 12610 I MultiTouchBugWorkaroundActivity:             1.841  628/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,1.11250007,0.229980469]A?  1: 1668.42078,739.486450,1.18750000,0.234863281     2: 1500.97900,1092.74121,0.949999988,0.222167969 B?  3:[371.870880,642.553772,0.687500000,0.187988281]
04-27 07:46:53.842 12610 12610 I MultiTouchBugWorkaroundActivity:             1.841  629/865:                  {0, 1, 2, 3}        0: 2093.27319,314.781403,1.11250007,0.230957031     1:[1668.42078,739.486450,1.18750000,0.234863281]    2:[1500.97900,1092.74121,0.949999988,0.222167969]B?  3:[371.870880,642.553772,0.687500000,0.187988281]
04-27 07:46:53.843 12610 12610 I MultiTouchBugWorkaroundActivity:             1.841  630/865:                  {0, 1, 2, 3}        0: 1960.31934,284.802216,1.11250007,0.230957031 A?  1:[1668.42078,739.486450,1.18750000,0.234863281]    2: 1596.44568,1230.14575,0.975000024,0.222656250     3:[371.870880,642.553772,0.687500000,0.187988281]
04-27 07:46:53.843 12610 12610 I MultiTouchBugWorkaroundActivity:             1.841  631/865:           MOVE   {0, 1, 2, 3}        0:[1960.31934,284.802216,1.11250007,0.230957031]A?  1:[1668.42078,739.486450,1.18750000,0.234863281]    2: 1500.97900,1092.74121,0.975000024,0.222656250 B?  3:(371.870880,642.553772,0.725000024,0.193359375)
04-27 07:46:53.843 12610 12610 I MultiTouchBugWorkaroundActivity:             1.854  632/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,1.11250007,0.230957031]A?  1: 1683.41553,688.521851,1.13750005,0.235351563     2:[1500.97900,1092.74121,0.975000024,0.222656250]B?  3:[371.870880,642.553772,0.725000024,0.193359375]
04-27 07:46:53.844 12610 12610 I MultiTouchBugWorkaroundActivity:             1.854  633/865:                  {0, 1, 2, 3}        0: 2106.26880,279.805695,1.12500000,0.230957031     1:[1683.41553,688.521851,1.13750005,0.235351563]    2:[1500.97900,1092.74121,0.975000024,0.222656250]B?  3:[371.870880,642.553772,0.725000024,0.193359375]
04-27 07:46:53.844 12610 12610 I MultiTouchBugWorkaroundActivity:             1.854  634/865:                  {0, 1, 2, 3}        0: 1960.31934,284.802216,1.12500000,0.230957031 A?  1:[1683.41553,688.521851,1.13750005,0.235351563]    2: 1604.44299,1194.17065,0.987500012,0.225585938     3:[371.870880,642.553772,0.725000024,0.193359375]
04-27 07:46:53.844 12610 12610 I MultiTouchBugWorkaroundActivity:             1.854  635/865:           MOVE   {0, 1, 2, 3}        0:[1960.31934,284.802216,1.12500000,0.230957031]A?  1:[1683.41553,688.521851,1.13750005,0.235351563]    2: 1500.97900,1092.74121,0.987500012,0.225585938 B?  3:(371.870880,642.553772,0.750000000,0.198730469)
04-27 07:46:53.844 12610 12610 I MultiTouchBugWorkaroundActivity:             1.866  636/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,1.12500000,0.230957031]A?  1: 1700.40967,628.563477,1.10000002,0.234375000     2:[1500.97900,1092.74121,0.987500012,0.225585938]B?  3:[371.870880,642.553772,0.750000000,0.198730469]
04-27 07:46:53.845 12610 12610 I MultiTouchBugWorkaroundActivity:             1.866  637/865:                  {0, 1, 2, 3}        0: 2114.26587,249.826508,1.13750005,0.230957031     1:[1700.40967,628.563477,1.10000002,0.234375000]    2:[1500.97900,1092.74121,0.987500012,0.225585938]B?  3:[371.870880,642.553772,0.750000000,0.198730469]
04-27 07:46:53.845 12610 12610 I MultiTouchBugWorkaroundActivity:             1.866  638/865:                  {0, 1, 2, 3}        0: 1960.31934,284.802216,1.13750005,0.230957031 A?  1:[1700.40967,628.563477,1.10000002,0.234375000]    2: 1609.44116,1162.19287,1.00000000,0.225585938     3:[371.870880,642.553772,0.750000000,0.198730469]
04-27 07:46:53.845 12610 12610 I MultiTouchBugWorkaroundActivity:             1.866  639/865:           MOVE   {0, 1, 2, 3}        0:[1960.31934,284.802216,1.13750005,0.230957031]A?  1:[1700.40967,628.563477,1.10000002,0.234375000]    2: 1500.97900,1092.74121,1.00000000,0.225585938 B?  3:(371.870880,642.553772,0.775000036,0.200195313)
04-27 07:46:53.846 12610 12610 I MultiTouchBugWorkaroundActivity:             1.877  640/865:    POINTER_UP(1) {0, 1, 2, 3}        0:[1960.31934,284.802216,1.13750005,0.230957031]A?  1:[1700.40967,628.563477,1.10000002,0.234375000]    2:[1500.97900,1092.74121,1.00000000,0.225585938]B?  3:[371.870880,642.553772,0.775000036,0.200195313]
04-27 07:46:53.846 12610 12610 I MultiTouchBugWorkaroundActivity:             1.877  641/865:                  {0, 2, 3}           0: 2117.26489,221.845932,1.13750005,0.230957031                                                         2:[1500.97900,1092.74121,1.00000000,0.225585938]B?  3:[371.870880,642.553772,0.775000036,0.200195313]
04-27 07:46:53.846 12610 12610 I MultiTouchBugWorkaroundActivity:             1.877  642/865:                  {0, 2, 3}           0: 1960.31934,284.802216,1.13750005,0.230957031 A?                                                      2: 1611.44055,1131.21448,1.00000000,0.224609375     3:[371.870880,642.553772,0.775000036,0.200195313]
04-27 07:46:53.847 12610 12610 I MultiTouchBugWorkaroundActivity:             1.877  643/865:           MOVE   {0, 2, 3}           0:[1960.31934,284.802216,1.13750005,0.230957031]A?                                                      2: 1500.97900,1092.74121,1.00000000,0.224609375 B?  3: 377.868805,639.555847,0.787500024,0.200195313
04-27 07:46:53.847 12610 12610 I MultiTouchBugWorkaroundActivity:             1.888  644/865:                  {0, 2, 3}           0: 2116.26514,198.861893,1.11250007,0.229980469                                                         2:[1500.97900,1092.74121,1.00000000,0.224609375]B?  3:[377.868805,639.555847,0.787500024,0.200195313]
04-27 07:46:53.847 12610 12610 I MultiTouchBugWorkaroundActivity:             1.888  645/865:                  {0, 2, 3}           0: 1960.31934,284.802216,1.11250007,0.229980469 A?                                                      2: 1611.44055,1105.23242,0.987500012,0.225585938     3:[377.868805,639.555847,0.787500024,0.200195313]
04-27 07:46:53.847 12610 12610 I MultiTouchBugWorkaroundActivity:             1.888  646/865:           MOVE   {0, 2, 3}           0:[1960.31934,284.802216,1.11250007,0.229980469]A?                                                      2: 1500.97900,1092.74121,0.987500012,0.225585938 B?  3: 388.864990,632.560730,0.787500024,0.199218750
04-27 07:46:53.848 12610 12610 I MultiTouchBugWorkaroundActivity:             1.888  647/865:  POINTER_DOWN(1) {0, 1, 2, 3}        0:[1960.31934,284.802216,1.11250007,0.229980469]A?  1: 899.687622,811.436462,1.11250007,0.225585938     2:[1500.97900,1092.74121,0.987500012,0.225585938]B?  3:[388.864990,632.560730,0.787500024,0.199218750]
04-27 07:46:53.848 12610 12610 I MultiTouchBugWorkaroundActivity:             1.899  648/865:                  {0, 1, 2, 3}        0: 2111.26709,179.875092,1.07500005,0.229003906     1:[899.687622,811.436462,1.11250007,0.225585938]    2:[1500.97900,1092.74121,0.987500012,0.225585938]B?  3:[388.864990,632.560730,0.787500024,0.199218750]
04-27 07:46:53.848 12610 12610 I MultiTouchBugWorkaroundActivity:             1.899  649/865:                  {0, 1, 2, 3}        0: 1960.31934,284.802216,1.07500005,0.229003906 A?  1:[899.687622,811.436462,1.11250007,0.225585938]    2: 1610.44092,1077.25195,0.975000024,0.224609375     3:[388.864990,632.560730,0.787500024,0.199218750]
04-27 07:46:53.849 12610 12610 I MultiTouchBugWorkaroundActivity:             1.899  650/865:                  {0, 1, 2, 3}        0:[1960.31934,284.802216,1.07500005,0.229003906]A?  1:[899.687622,811.436462,1.11250007,0.225585938]    2: 1500.97900,1092.74121,0.975000024,0.224609375 B?  3: 403.859772,623.566956,0.775000036,0.200195313
04-27 07:46:53.849 12610 12610 I MultiTouchBugWorkaroundActivity:             1.899  651/865:           MOVE   {0, 1, 2, 3}        0:[1960.31934,284.802216,1.07500005,0.229003906]A?  1:(899.687622,811.436462,1.12500000,0.225585938)    2:[1500.97900,1092.74121,0.975000024,0.224609375]B?  3:[403.859772,623.566956,0.775000036,0.200195313]
04-27 07:46:53.849 12610 12610 I MultiTouchBugWorkaroundActivity:             1.909  652/865:    POINTER_UP(0) {0, 1, 2, 3}        0:[1960.31934,284.802216,1.07500005,0.229003906]A?  1:[899.687622,811.436462,1.12500000,0.225585938]    2:[1500.97900,1092.74121,0.975000024,0.224609375]B?  3:[403.859772,623.566956,0.775000036,0.200195313]
04-27 07:46:53.849 12610 12610 I MultiTouchBugWorkaroundActivity:             1.909  653/865:                  {1, 2, 3}                                                               1:[899.687622,811.436462,1.12500000,0.225585938]    2: 1608.44153,1053.26855,0.962500036,0.223632813     3:[403.859772,623.566956,0.775000036,0.200195313]
04-27 07:46:53.850 12610 12610 I MultiTouchBugWorkaroundActivity:             1.909  654/865:                  {1, 2, 3}                                                               1:[899.687622,811.436462,1.12500000,0.225585938]    2:[1608.44153,1053.26855,0.962500036,0.223632813]    3: 422.853180,612.574585,0.750000000,0.197753906
04-27 07:46:53.850 12610 12610 I MultiTouchBugWorkaroundActivity:             1.909  655/865:           MOVE   {1, 2, 3}                                                               1:(899.687622,811.436462,1.13750005,0.227539063)    2:[1608.44153,1053.26855,0.962500036,0.223632813]    3:[422.853180,612.574585,0.750000000,0.197753906]
04-27 07:46:53.850 12610 12610 I MultiTouchBugWorkaroundActivity:             1.916  656/865:           MOVE   {1, 2, 3}                                                               1:[899.687622,811.436462,1.13750005,0.227539063]    2: 1602.44360,1034.28174,0.949999988,0.222656250     3:[422.853180,612.574585,0.750000000,0.197753906]
04-27 07:46:53.851 12610 12610 I MultiTouchBugWorkaroundActivity:             1.916  657/865:    POINTER_UP(3) {1, 2, 3}                                                               1:[899.687622,811.436462,1.13750005,0.227539063]    2:[1602.44360,1034.28174,0.949999988,0.222656250]    3:[422.853180,612.574585,0.750000000,0.197753906]
04-27 07:46:53.851 12610 12610 I MultiTouchBugWorkaroundActivity:             1.916  658/865:           MOVE   {1, 2}                                                                  1:(899.687622,811.436462,1.13750005,0.228515625)    2:[1602.44360,1034.28174,0.949999988,0.222656250]
04-27 07:46:53.851 12610 12610 I MultiTouchBugWorkaroundActivity:             1.924  659/865:                  {1, 2}                                                                  1:[899.687622,811.436462,1.13750005,0.228515625]    2: 1595.44604,1023.28937,0.949999988,0.222656250
04-27 07:46:53.851 12610 12610 I MultiTouchBugWorkaroundActivity:             1.924  660/865:           MOVE   {1, 2}                                                                  1: 904.685913,808.438599,1.13750005,0.228515625     2:[1595.44604,1023.28937,0.949999988,0.222656250]
04-27 07:46:53.851 12610 12610 I MultiTouchBugWorkaroundActivity:             1.932  661/865:                  {1, 2}                                                                  1:[904.685913,808.438599,1.13750005,0.228515625]    2: 1588.44849,1019.29211,0.949999988,0.222656250
04-27 07:46:53.851 12610 12610 I MultiTouchBugWorkaroundActivity:             1.932  662/865:           MOVE   {1, 2}                                                                  1: 913.682800,803.442017,1.12500000,0.227539063     2:[1588.44849,1019.29211,0.949999988,0.222656250]
04-27 07:46:53.852 12610 12610 I MultiTouchBugWorkaroundActivity:             1.940  663/865:                  {1, 2}                                                                  1:[913.682800,803.442017,1.12500000,0.227539063]    2: 1584.44983,1021.29077,0.975000024,0.223632813
04-27 07:46:53.852 12610 12610 I MultiTouchBugWorkaroundActivity:             1.940  664/865:           MOVE   {1, 2}                                                                  1: 922.679626,797.446228,1.10000002,0.225585938     2:[1584.44983,1021.29077,0.975000024,0.223632813]
04-27 07:46:53.852 12610 12610 I MultiTouchBugWorkaroundActivity:             1.948  665/865:           MOVE   {1, 2}                                                                  1:[922.679626,797.446228,1.10000002,0.225585938]    2: 1581.45093,1025.28796,0.987500012,0.225585938
04-27 07:46:53.852 12610 12610 I MultiTouchBugWorkaroundActivity:             1.948  666/865:    POINTER_UP(1) {1, 2}                                                                  1:[922.679626,797.446228,1.10000002,0.225585938]    2:[1581.45093,1025.28796,0.987500012,0.225585938]
04-27 07:46:53.852 12610 12610 I MultiTouchBugWorkaroundActivity:             1.957  667/865:                  {2}                                                                                                                         2: 1580.45129,1027.28662,1.01250005,0.229003906
04-27 07:46:53.853 12610 12610 I MultiTouchBugWorkaroundActivity:             1.961  668/865:           MOVE   {2}                                                                                                                         2: 1579.95142,1028.28589,1.01250005,0.229003906
04-27 07:46:53.853 12610 12610 I MultiTouchBugWorkaroundActivity:             1.965  669/865:           MOVE   {2}                                                                                                                         2: 1581.45093,1029.28516,1.02499998,0.229003906
04-27 07:46:53.853 12610 12610 I MultiTouchBugWorkaroundActivity:             1.974  670/865:  POINTER_DOWN(0) {0, 2}              0: 1627.43494,585.593323,0.862500012,0.203613281 A!                                                      2:[1581.45093,1029.28516,1.02499998,0.229003906]
04-27 07:46:53.853 12610 12610 I MultiTouchBugWorkaroundActivity:             1.974  671/865:                  {0, 2}              0:[1627.43494,585.593323,0.862500012,0.203613281]A                                                       2: 1584.44983,1031.28381,1.03750002,0.231445313
04-27 07:46:53.853 12610 12610 I MultiTouchBugWorkaroundActivity:             1.979  672/865:           MOVE   {0, 2}              0:[1627.43494,585.593323,0.862500012,0.203613281]A                                                       2: 1585.94922,1032.28320,1.03750002,0.231445313 C!
04-27 07:46:53.853 12610 12610 I MultiTouchBugWorkaroundActivity:             1.983  673/865:                  {0, 2}              0:(1627.43494,585.593323,0.875000000,0.208496094)A                                                       2:[1585.94922,1032.28320,1.03750002,0.231445313]C?
04-27 07:46:53.854 12610 12610 I MultiTouchBugWorkaroundActivity:             1.983  674/865:           MOVE   {0, 2}              0:[1627.43494,585.593323,0.875000000,0.208496094]A                                                       2: 1588.44849,1034.28174,1.05000007,0.233398438
04-27 07:46:53.854 12610 12610 I MultiTouchBugWorkaroundActivity:             1.993  675/865:                  {0, 2}              0:(1627.43494,585.593323,0.900000036,0.212890625)A                                                       2: 1585.94922,1032.28320,1.05000007,0.233398438 C?
04-27 07:46:53.854 12610 12610 I MultiTouchBugWorkaroundActivity:             1.993  676/865:           MOVE   {0, 2}              0:[1627.43494,585.593323,0.900000036,0.212890625]A                                                       2: 1593.44678,1039.27832,1.06250000,0.234863281
04-27 07:46:53.854 12610 12610 I MultiTouchBugWorkaroundActivity:             2.004  677/865:           MOVE   {0, 2}              0:(1627.43494,585.593323,0.912500024,0.215820313)A                                                       2: 1585.94922,1032.28320,1.06250000,0.234863281 C?
04-27 07:46:53.855 12610 12610 I MultiTouchBugWorkaroundActivity:             2.004  678/865:  POINTER_DOWN(1) {0, 1, 2}           0:[1627.43494,585.593323,0.912500024,0.215820313]A   1: 2074.27979,233.837616,1.08749998,0.227539063     2:[1585.94922,1032.28320,1.06250000,0.234863281]C?
04-27 07:46:53.855 12610 12610 I MultiTouchBugWorkaroundActivity:             2.004  679/865:           MOVE   {0, 1, 2}           0:[1627.43494,585.593323,0.912500024,0.215820313]A   1:[2074.27979,233.837616,1.08749998,0.227539063]    2: 1599.44470,1044.27478,1.06250000,0.234863281
04-27 07:46:53.855 12610 12610 I MultiTouchBugWorkaroundActivity:             2.016  680/865:                  {0, 1, 2}           0:(1627.43494,585.593323,0.937500000,0.218261719)A   1:[2074.27979,233.837616,1.08749998,0.227539063]    2: 1585.94922,1032.28320,1.06250000,0.234863281 C?
04-27 07:46:53.855 12610 12610 I MultiTouchBugWorkaroundActivity:             2.016  681/865:                  {0, 1, 2}           0:[1627.43494,585.593323,0.937500000,0.218261719]A   1:(2074.27979,233.837616,1.11250007,0.229003906)    2:[1585.94922,1032.28320,1.06250000,0.234863281]C?
04-27 07:46:53.856 12610 12610 I MultiTouchBugWorkaroundActivity:             2.016  682/865:           MOVE   {0, 1, 2}           0:[1627.43494,585.593323,0.937500000,0.218261719]A   1:[2074.27979,233.837616,1.11250007,0.229003906]    2: 1606.44226,1052.26929,1.07500005,0.234863281
04-27 07:46:53.856 12610 12610 I MultiTouchBugWorkaroundActivity:             2.030  683/865:                  {0, 1, 2}           0: 1632.43323,589.590576,0.949999988,0.219238281     1:[2074.27979,233.837616,1.11250007,0.229003906]    2: 1585.94922,1032.28320,1.07500005,0.234863281 C?
04-27 07:46:53.856 12610 12610 I MultiTouchBugWorkaroundActivity:             2.030  684/865:                  {0, 1, 2}           0: 1627.43494,585.593323,0.949999988,0.219238281 A?  1:(2074.27979,233.837616,1.12500000,0.229003906)    2:[1585.94922,1032.28320,1.07500005,0.234863281]C?
04-27 07:46:53.856 12610 12610 I MultiTouchBugWorkaroundActivity:             2.030  685/865:                  {0, 1, 2}           0:[1627.43494,585.593323,0.949999988,0.219238281]A?  1:[2074.27979,233.837616,1.12500000,0.229003906]    2: 1614.43945,1064.26086,1.07500005,0.234863281
04-27 07:46:53.857 12610 12610 I MultiTouchBugWorkaroundActivity:             2.044  686/865:                  {0, 1, 2}           0: 1642.42981,598.584290,0.975000024,0.221191406     1:[2074.27979,233.837616,1.12500000,0.229003906]    2: 1585.94922,1032.28320,1.07500005,0.234863281 C?
04-27 07:46:53.857 12610 12610 I MultiTouchBugWorkaroundActivity:             2.044  687/865:                  {0, 1, 2}           0: 1627.43494,585.593323,0.975000024,0.221191406 A?  1:(2074.27979,233.837616,1.13750005,0.229980469)    2:[1585.94922,1032.28320,1.07500005,0.234863281]C?
04-27 07:46:53.857 12610 12610 I MultiTouchBugWorkaroundActivity:             2.044  688/865:           MOVE   {0, 1, 2}           0:[1627.43494,585.593323,0.975000024,0.221191406]A?  1:[2074.27979,233.837616,1.13750005,0.229980469]    2: 1624.43604,1077.25195,1.07500005,0.234863281
04-27 07:46:53.857 12610 12610 I MultiTouchBugWorkaroundActivity:             2.044  689/865:  POINTER_DOWN(3) {0, 1, 2, 3}        0:[1627.43494,585.593323,0.975000024,0.221191406]A?  1:[2074.27979,233.837616,1.13750005,0.229980469]    2:[1624.43604,1077.25195,1.07500005,0.234863281]    3: 500.826111,618.570435,0.762499988,0.189941406
04-27 07:46:53.858 12610 12610 I MultiTouchBugWorkaroundActivity:             2.044  690/865:  POINTER_DOWN(4) {0, 1, 2, 3, 4}     0:[1627.43494,585.593323,0.975000024,0.221191406]A?  1:[2074.27979,233.837616,1.13750005,0.229980469]    2:[1624.43604,1077.25195,1.07500005,0.234863281]    3:[500.826111,618.570435,0.762499988,0.189941406]    4: 1189.58704,1271.11731,0.612500012,0.158203125
04-27 07:46:53.858 12610 12610 I MultiTouchBugWorkaroundActivity:             2.058  691/865:                  {0, 1, 2, 3, 4}     0: 1656.42493,608.577393,0.987500012,0.222656250     1:[2074.27979,233.837616,1.13750005,0.229980469]    2: 1585.94922,1032.28320,1.07500005,0.234863281 C?  3:[500.826111,618.570435,0.762499988,0.189941406]    4:[1189.58704,1271.11731,0.612500012,0.158203125]
04-27 07:46:53.859 12610 12610 I MultiTouchBugWorkaroundActivity:             2.058  692/865:                  {0, 1, 2, 3, 4}     0: 1627.43494,585.593323,0.987500012,0.222656250 A?  1: 2081.27734,237.834839,1.14999998,0.231933594     2:[1585.94922,1032.28320,1.07500005,0.234863281]C?  3:[500.826111,618.570435,0.762499988,0.189941406]    4:[1189.58704,1271.11731,0.612500012,0.158203125]
04-27 07:46:53.859 12610 12610 I MultiTouchBugWorkaroundActivity:             2.058  693/865:                  {0, 1, 2, 3, 4}     0:[1627.43494,585.593323,0.987500012,0.222656250]A?  1:[2081.27734,237.834839,1.14999998,0.231933594]    2: 1639.43079,1096.23865,1.06250000,0.233398438     3:[500.826111,618.570435,0.762499988,0.189941406]    4:[1189.58704,1271.11731,0.612500012,0.158203125]
04-27 07:46:53.859 12610 12610 I MultiTouchBugWorkaroundActivity:             2.058  694/865:                  {0, 1, 2, 3, 4}     0:[1627.43494,585.593323,0.987500012,0.222656250]A?  1:[2081.27734,237.834839,1.14999998,0.231933594]    2: 1585.94922,1032.28320,1.06250000,0.233398438 C?  3:(500.826111,618.570435,0.800000012,0.195312500)    4:[1189.58704,1271.11731,0.612500012,0.158203125]
04-27 07:46:53.860 12610 12610 I MultiTouchBugWorkaroundActivity:             2.058  695/865:           MOVE   {0, 1, 2, 3, 4}     0:[1627.43494,585.593323,0.987500012,0.222656250]A?  1:[2081.27734,237.834839,1.14999998,0.231933594]    2:[1585.94922,1032.28320,1.06250000,0.233398438]C?  3:[500.826111,618.570435,0.800000012,0.195312500]    4:(1189.58704,1271.11731,0.637499988,0.164062500)
04-27 07:46:53.860 12610 12610 I MultiTouchBugWorkaroundActivity:             2.058  696/865:  POINTER_DOWN(5) {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,0.987500012,0.222656250]A?  1:[2081.27734,237.834839,1.14999998,0.231933594]    2:[1585.94922,1032.28320,1.06250000,0.233398438]C?  3:[500.826111,618.570435,0.800000012,0.195312500]    4:[1189.58704,1271.11731,0.637499988,0.164062500]    5: 949.670288,840.416382,0.612500012,0.191894531
04-27 07:46:53.861 12610 12610 I MultiTouchBugWorkaroundActivity:             2.071  697/865:                  {0, 1, 2, 3, 4, 5}  0: 1677.41760,621.568359,1.00000000,0.223632813     1:[2081.27734,237.834839,1.14999998,0.231933594]    2:[1585.94922,1032.28320,1.06250000,0.233398438]C?  3:[500.826111,618.570435,0.800000012,0.195312500]    4:[1189.58704,1271.11731,0.637499988,0.164062500]    5:[949.670288,840.416382,0.612500012,0.191894531]
04-27 07:46:53.861 12610 12610 I MultiTouchBugWorkaroundActivity:             2.071  698/865:                  {0, 1, 2, 3, 4, 5}  0: 1627.43494,585.593323,1.00000000,0.223632813 A?  1: 2096.27222,247.827896,1.14999998,0.232421875     2:[1585.94922,1032.28320,1.06250000,0.233398438]C?  3:[500.826111,618.570435,0.800000012,0.195312500]    4:[1189.58704,1271.11731,0.637499988,0.164062500]    5:[949.670288,840.416382,0.612500012,0.191894531]
04-27 07:46:53.862 12610 12610 I MultiTouchBugWorkaroundActivity:             2.071  699/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.00000000,0.223632813]A?  1:[2096.27222,247.827896,1.14999998,0.232421875]    2: 1661.42310,1117.22412,1.06250000,0.232421875     3:[500.826111,618.570435,0.800000012,0.195312500]    4:[1189.58704,1271.11731,0.637499988,0.164062500]    5:[949.670288,840.416382,0.612500012,0.191894531]
04-27 07:46:53.862 12610 12610 I MultiTouchBugWorkaroundActivity:             2.071  700/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.00000000,0.223632813]A?  1:[2096.27222,247.827896,1.14999998,0.232421875]    2: 1585.94922,1032.28320,1.06250000,0.232421875 C?  3:(500.826111,618.570435,0.824999988,0.200195313)    4:[1189.58704,1271.11731,0.637499988,0.164062500]    5:[949.670288,840.416382,0.612500012,0.191894531]
04-27 07:46:53.862 12610 12610 I MultiTouchBugWorkaroundActivity:             2.071  701/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.00000000,0.223632813]A?  1:[2096.27222,247.827896,1.14999998,0.232421875]    2:[1585.94922,1032.28320,1.06250000,0.232421875]C?  3:[500.826111,618.570435,0.824999988,0.200195313]    4:(1189.58704,1271.11731,0.662500024,0.167480469)    5:[949.670288,840.416382,0.612500012,0.191894531]
04-27 07:46:53.863 12610 12610 I MultiTouchBugWorkaroundActivity:             2.071  702/865:           MOVE   {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.00000000,0.223632813]A?  1:[2096.27222,247.827896,1.14999998,0.232421875]    2:[1585.94922,1032.28320,1.06250000,0.232421875]C?  3:[500.826111,618.570435,0.824999988,0.200195313]    4:[1189.58704,1271.11731,0.662500024,0.167480469]    5:(949.670288,840.416382,0.637499988,0.193847656)
04-27 07:46:53.863 12610 12610 I MultiTouchBugWorkaroundActivity:             2.085  703/865:                  {0, 1, 2, 3, 4, 5}  0: 1707.40723,639.555847,1.01250005,0.223632813     1:[2096.27222,247.827896,1.14999998,0.232421875]    2:[1585.94922,1032.28320,1.06250000,0.232421875]C?  3:[500.826111,618.570435,0.824999988,0.200195313]    4:[1189.58704,1271.11731,0.662500024,0.167480469]    5:[949.670288,840.416382,0.637499988,0.193847656]
04-27 07:46:53.865 12610 12610 I MultiTouchBugWorkaroundActivity:             2.085  704/865:                  {0, 1, 2, 3, 4, 5}  0: 1627.43494,585.593323,1.01250005,0.223632813 A?  1: 2117.26489,260.818878,1.16250002,0.232421875     2:[1585.94922,1032.28320,1.06250000,0.232421875]C?  3:[500.826111,618.570435,0.824999988,0.200195313]    4:[1189.58704,1271.11731,0.662500024,0.167480469]    5:[949.670288,840.416382,0.637499988,0.193847656]
04-27 07:46:53.865 12610 12610 I MultiTouchBugWorkaroundActivity:             2.085  705/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.01250005,0.223632813]A?  1:[2117.26489,260.818878,1.16250002,0.232421875]    2: 1696.41101,1144.20544,1.05000007,0.230468750     3:[500.826111,618.570435,0.824999988,0.200195313]    4:[1189.58704,1271.11731,0.662500024,0.167480469]    5:[949.670288,840.416382,0.637499988,0.193847656]
04-27 07:46:53.866 12610 12610 I MultiTouchBugWorkaroundActivity:             2.085  706/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.01250005,0.223632813]A?  1:[2117.26489,260.818878,1.16250002,0.232421875]    2: 1585.94922,1032.28320,1.05000007,0.230468750 C?  3:(500.826111,618.570435,0.837500036,0.204101563)    4:[1189.58704,1271.11731,0.662500024,0.167480469]    5:[949.670288,840.416382,0.637499988,0.193847656]
04-27 07:46:53.866 12610 12610 I MultiTouchBugWorkaroundActivity:             2.085  707/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.01250005,0.223632813]A?  1:[2117.26489,260.818878,1.16250002,0.232421875]    2:[1585.94922,1032.28320,1.05000007,0.230468750]C?  3:[500.826111,618.570435,0.837500036,0.204101563]    4:(1189.58704,1271.11731,0.687500000,0.171875000)    5:[949.670288,840.416382,0.637499988,0.193847656]
04-27 07:46:53.867 12610 12610 I MultiTouchBugWorkaroundActivity:             2.085  708/865:           MOVE   {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.01250005,0.223632813]A?  1:[2117.26489,260.818878,1.16250002,0.232421875]    2:[1585.94922,1032.28320,1.05000007,0.230468750]C?  3:[500.826111,618.570435,0.837500036,0.204101563]    4:[1189.58704,1271.11731,0.687500000,0.171875000]    5:(949.670288,840.416382,0.662500024,0.197265625)
04-27 07:46:53.867 12610 12610 I MultiTouchBugWorkaroundActivity:             2.099  709/865:                  {0, 1, 2, 3, 4, 5}  0: 1745.39404,661.540588,1.01250005,0.225585938     1:[2117.26489,260.818878,1.16250002,0.232421875]    2:[1585.94922,1032.28320,1.05000007,0.230468750]C?  3:[500.826111,618.570435,0.837500036,0.204101563]    4:[1189.58704,1271.11731,0.687500000,0.171875000]    5:[949.670288,840.416382,0.662500024,0.197265625]
04-27 07:46:53.868 12610 12610 I MultiTouchBugWorkaroundActivity:             2.099  710/865:                  {0, 1, 2, 3, 4, 5}  0: 1627.43494,585.593323,1.01250005,0.225585938 A?  1: 2147.25439,278.806366,1.17500007,0.233398438     2:[1585.94922,1032.28320,1.05000007,0.230468750]C?  3:[500.826111,618.570435,0.837500036,0.204101563]    4:[1189.58704,1271.11731,0.687500000,0.171875000]    5:[949.670288,840.416382,0.662500024,0.197265625]
04-27 07:46:53.870 12610 12610 I MultiTouchBugWorkaroundActivity:             2.099  711/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.01250005,0.225585938]A?  1:[2147.25439,278.806366,1.17500007,0.233398438]    2: 1733.39819,1168.18872,1.03750002,0.229492188     3:[500.826111,618.570435,0.837500036,0.204101563]    4:[1189.58704,1271.11731,0.687500000,0.171875000]    5:[949.670288,840.416382,0.662500024,0.197265625]
04-27 07:46:53.870 12610 12610 I MultiTouchBugWorkaroundActivity:             2.099  712/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.01250005,0.225585938]A?  1:[2147.25439,278.806366,1.17500007,0.233398438]    2: 1585.94922,1032.28320,1.03750002,0.229492188 C?  3: 496.827515,621.568359,0.850000024,0.204589844     4:[1189.58704,1271.11731,0.687500000,0.171875000]    5:[949.670288,840.416382,0.662500024,0.197265625]
04-27 07:46:53.871 12610 12610 I MultiTouchBugWorkaroundActivity:             2.099  713/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.01250005,0.225585938]A?  1:[2147.25439,278.806366,1.17500007,0.233398438]    2:[1585.94922,1032.28320,1.03750002,0.229492188]C?  3:[496.827515,621.568359,0.850000024,0.204589844]    4: 1186.58801,1275.11450,0.699999988,0.173339844     5:[949.670288,840.416382,0.662500024,0.197265625]
04-27 07:46:53.871 12610 12610 I MultiTouchBugWorkaroundActivity:             2.099  714/865:           MOVE   {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.01250005,0.225585938]A?  1:[2147.25439,278.806366,1.17500007,0.233398438]    2:[1585.94922,1032.28320,1.03750002,0.229492188]C?  3:[496.827515,621.568359,0.850000024,0.204589844]    4:[1186.58801,1275.11450,0.699999988,0.173339844]    5:(949.670288,840.416382,0.687500000,0.198242188)
04-27 07:46:53.872 12610 12610 I MultiTouchBugWorkaroundActivity:             2.112  715/865:                  {0, 1, 2, 3, 4, 5}  0: 1788.37903,684.524597,1.02499998,0.225585938     1:[2147.25439,278.806366,1.17500007,0.233398438]    2:[1585.94922,1032.28320,1.03750002,0.229492188]C?  3:[496.827515,621.568359,0.850000024,0.204589844]    4:[1186.58801,1275.11450,0.699999988,0.173339844]    5:[949.670288,840.416382,0.687500000,0.198242188]
04-27 07:46:53.872 12610 12610 I MultiTouchBugWorkaroundActivity:             2.112  716/865:                  {0, 1, 2, 3, 4, 5}  0: 1627.43494,585.593323,1.02499998,0.225585938 A?  1: 2186.24097,299.791809,1.17500007,0.234375000     2:[1585.94922,1032.28320,1.03750002,0.229492188]C?  3:[496.827515,621.568359,0.850000024,0.204589844]    4:[1186.58801,1275.11450,0.699999988,0.173339844]    5:[949.670288,840.416382,0.687500000,0.198242188]
04-27 07:46:53.873 12610 12610 I MultiTouchBugWorkaroundActivity:             2.112  717/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.02499998,0.225585938]A?  1:[2186.24097,299.791809,1.17500007,0.234375000]    2: 1773.38428,1186.17627,1.03750002,0.228027344     3:[496.827515,621.568359,0.850000024,0.204589844]    4:[1186.58801,1275.11450,0.699999988,0.173339844]    5:[949.670288,840.416382,0.687500000,0.198242188]
04-27 07:46:53.873 12610 12610 I MultiTouchBugWorkaroundActivity:             2.112  718/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.02499998,0.225585938]A?  1:[2186.24097,299.791809,1.17500007,0.234375000]    2: 1585.94922,1032.28320,1.03750002,0.228027344 C?  3: 486.830963,629.562805,0.862500012,0.205566406     4:[1186.58801,1275.11450,0.699999988,0.173339844]    5:[949.670288,840.416382,0.687500000,0.198242188]
04-27 07:46:53.874 12610 12610 I MultiTouchBugWorkaroundActivity:             2.112  719/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.02499998,0.225585938]A?  1:[2186.24097,299.791809,1.17500007,0.234375000]    2:[1585.94922,1032.28320,1.03750002,0.228027344]C?  3:[486.830963,629.562805,0.862500012,0.205566406]    4: 1175.59180,1287.10620,0.712500036,0.175781250     5:[949.670288,840.416382,0.687500000,0.198242188]
04-27 07:46:53.874 12610 12610 I MultiTouchBugWorkaroundActivity:             2.112  720/865:           MOVE   {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.02499998,0.225585938]A?  1:[2186.24097,299.791809,1.17500007,0.234375000]    2:[1585.94922,1032.28320,1.03750002,0.228027344]C?  3:[486.830963,629.562805,0.862500012,0.205566406]    4:[1175.59180,1287.10620,0.712500036,0.175781250]    5:(949.670288,840.416382,0.712500036,0.199218750)
04-27 07:46:53.874 12610 12610 I MultiTouchBugWorkaroundActivity:             2.126  721/865:                  {0, 1, 2, 3, 4, 5}  0: 1833.36340,706.509338,1.03750002,0.226562500     1:[2186.24097,299.791809,1.17500007,0.234375000]    2:[1585.94922,1032.28320,1.03750002,0.228027344]C?  3:[486.830963,629.562805,0.862500012,0.205566406]    4:[1175.59180,1287.10620,0.712500036,0.175781250]    5:[949.670288,840.416382,0.712500036,0.199218750]
04-27 07:46:53.875 12610 12610 I MultiTouchBugWorkaroundActivity:             2.126  722/865:                  {0, 1, 2, 3, 4, 5}  0: 1627.43494,585.593323,1.03750002,0.226562500 A?  1: 2230.22559,321.776550,1.18750000,0.234863281     2:[1585.94922,1032.28320,1.03750002,0.228027344]C?  3:[486.830963,629.562805,0.862500012,0.205566406]    4:[1175.59180,1287.10620,0.712500036,0.175781250]    5:[949.670288,840.416382,0.712500036,0.199218750]
04-27 07:46:53.875 12610 12610 I MultiTouchBugWorkaroundActivity:             2.126  723/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.03750002,0.226562500]A?  1:[2230.22559,321.776550,1.18750000,0.234863281]    2: 1818.36865,1199.16724,1.02499998,0.229003906     3:[486.830963,629.562805,0.862500012,0.205566406]    4:[1175.59180,1287.10620,0.712500036,0.175781250]    5:[949.670288,840.416382,0.712500036,0.199218750]
04-27 07:46:53.876 12610 12610 I MultiTouchBugWorkaroundActivity:             2.126  724/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.03750002,0.226562500]A?  1:[2230.22559,321.776550,1.18750000,0.234863281]    2: 1585.94922,1032.28320,1.02499998,0.229003906 C?  3: 472.835846,638.556519,0.862500012,0.204589844     4:[1175.59180,1287.10620,0.712500036,0.175781250]    5:[949.670288,840.416382,0.712500036,0.199218750]
04-27 07:46:53.876 12610 12610 I MultiTouchBugWorkaroundActivity:             2.126  725/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.03750002,0.226562500]A?  1:[2230.22559,321.776550,1.18750000,0.234863281]    2:[1585.94922,1032.28320,1.02499998,0.229003906]C?  3:[472.835846,638.556519,0.862500012,0.204589844]    4: 1162.59631,1299.09778,0.737500012,0.176269531     5:[949.670288,840.416382,0.712500036,0.199218750]
04-27 07:46:53.877 12610 12610 I MultiTouchBugWorkaroundActivity:             2.126  726/865:           MOVE   {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.03750002,0.226562500]A?  1:[2230.22559,321.776550,1.18750000,0.234863281]    2:[1585.94922,1032.28320,1.02499998,0.229003906]C?  3:[472.835846,638.556519,0.862500012,0.204589844]    4:[1162.59631,1299.09778,0.737500012,0.176269531]    5: 946.671326,842.414978,0.725000024,0.199218750
04-27 07:46:53.877 12610 12610 I MultiTouchBugWorkaroundActivity:             2.139  727/865:                  {0, 1, 2, 3, 4, 5}  0: 1881.34680,727.494812,1.03750002,0.227050781     1:[2230.22559,321.776550,1.18750000,0.234863281]    2:[1585.94922,1032.28320,1.02499998,0.229003906]C?  3:[472.835846,638.556519,0.862500012,0.204589844]    4:[1162.59631,1299.09778,0.737500012,0.176269531]    5:[946.671326,842.414978,0.725000024,0.199218750]
04-27 07:46:53.877 12610 12610 I MultiTouchBugWorkaroundActivity:             2.139  728/865:                  {0, 1, 2, 3, 4, 5}  0: 1627.43494,585.593323,1.03750002,0.227050781 A?  1: 2278.20898,341.762665,1.18750000,0.234863281     2:[1585.94922,1032.28320,1.02499998,0.229003906]C?  3:[472.835846,638.556519,0.862500012,0.204589844]    4:[1162.59631,1299.09778,0.737500012,0.176269531]    5:[946.671326,842.414978,0.725000024,0.199218750]
04-27 07:46:53.878 12610 12610 I MultiTouchBugWorkaroundActivity:             2.139  729/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.03750002,0.227050781]A?  1:[2278.20898,341.762665,1.18750000,0.234863281]    2: 1864.35266,1210.15955,1.01250005,0.227050781     3:[472.835846,638.556519,0.862500012,0.204589844]    4:[1162.59631,1299.09778,0.737500012,0.176269531]    5:[946.671326,842.414978,0.725000024,0.199218750]
04-27 07:46:53.878 12610 12610 I MultiTouchBugWorkaroundActivity:             2.139  730/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.03750002,0.227050781]A?  1:[2278.20898,341.762665,1.18750000,0.234863281]    2: 1585.94922,1032.28320,1.01250005,0.227050781 C?  3: 453.842438,649.548889,0.862500012,0.205566406     4:[1162.59631,1299.09778,0.737500012,0.176269531]    5:[946.671326,842.414978,0.725000024,0.199218750]
04-27 07:46:53.879 12610 12610 I MultiTouchBugWorkaroundActivity:             2.139  731/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.03750002,0.227050781]A?  1:[2278.20898,341.762665,1.18750000,0.234863281]    2:[1585.94922,1032.28320,1.01250005,0.227050781]C?  3:[453.842438,649.548889,0.862500012,0.205566406]    4: 1143.60291,1314.08740,0.750000000,0.176269531     5:[946.671326,842.414978,0.725000024,0.199218750]
04-27 07:46:53.879 12610 12610 I MultiTouchBugWorkaroundActivity:             2.139  732/865:           MOVE   {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.03750002,0.227050781]A?  1:[2278.20898,341.762665,1.18750000,0.234863281]    2:[1585.94922,1032.28320,1.01250005,0.227050781]C?  3:[453.842438,649.548889,0.862500012,0.205566406]    4:[1143.60291,1314.08740,0.750000000,0.176269531]    5: 937.674438,847.411499,0.737500012,0.199218750
04-27 07:46:53.880 12610 12610 I MultiTouchBugWorkaroundActivity:             2.152  733/865:                  {0, 1, 2, 3, 4, 5}  0: 1930.32983,744.482971,1.03750002,0.227050781     1:[2278.20898,341.762665,1.18750000,0.234863281]    2:[1585.94922,1032.28320,1.01250005,0.227050781]C?  3:[453.842438,649.548889,0.862500012,0.205566406]    4:[1143.60291,1314.08740,0.750000000,0.176269531]    5:[937.674438,847.411499,0.737500012,0.199218750]
04-27 07:46:53.880 12610 12610 I MultiTouchBugWorkaroundActivity:             2.152  734/865:                  {0, 1, 2, 3, 4, 5}  0: 1627.43494,585.593323,1.03750002,0.227050781 A?  1: 2327.19189,356.752258,1.18750000,0.234863281     2:[1585.94922,1032.28320,1.01250005,0.227050781]C?  3:[453.842438,649.548889,0.862500012,0.205566406]    4:[1143.60291,1314.08740,0.750000000,0.176269531]    5:[937.674438,847.411499,0.737500012,0.199218750]
04-27 07:46:53.881 12610 12610 I MultiTouchBugWorkaroundActivity:             2.152  735/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.03750002,0.227050781]A?  1:[2327.19189,356.752258,1.18750000,0.234863281]    2: 1911.33643,1219.15332,1.01250005,0.226562500     3:[453.842438,649.548889,0.862500012,0.205566406]    4:[1143.60291,1314.08740,0.750000000,0.176269531]    5:[937.674438,847.411499,0.737500012,0.199218750]
04-27 07:46:53.881 12610 12610 I MultiTouchBugWorkaroundActivity:             2.152  736/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.03750002,0.227050781]A?  1:[2327.19189,356.752258,1.18750000,0.234863281]    2: 1585.94922,1032.28320,1.01250005,0.226562500 C?  3: 429.850769,662.539917,0.850000024,0.204101563     4:[1143.60291,1314.08740,0.750000000,0.176269531]    5:[937.674438,847.411499,0.737500012,0.199218750]
04-27 07:46:53.881 12610 12610 I MultiTouchBugWorkaroundActivity:             2.152  737/865:                  {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.03750002,0.227050781]A?  1:[2327.19189,356.752258,1.18750000,0.234863281]    2:[1585.94922,1032.28320,1.01250005,0.226562500]C?  3:[429.850769,662.539917,0.850000024,0.204101563]    4: 1122.61023,1329.07703,0.750000000,0.176757813     5:[937.674438,847.411499,0.737500012,0.199218750]
04-27 07:46:53.882 12610 12610 I MultiTouchBugWorkaroundActivity:             2.152  738/865:           MOVE   {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.03750002,0.227050781]A?  1:[2327.19189,356.752258,1.18750000,0.234863281]    2:[1585.94922,1032.28320,1.01250005,0.226562500]C?  3:[429.850769,662.539917,0.850000024,0.204101563]    4:[1122.61023,1329.07703,0.750000000,0.176757813]    5: 926.678284,853.407349,0.725000024,0.199218750
04-27 07:46:53.882 12610 12610 I MultiTouchBugWorkaroundActivity:             2.164  739/865:                  {0, 1, 2, 3, 4, 5}  0: 1980.31238,757.473938,1.05000007,0.227050781     1:[2327.19189,356.752258,1.18750000,0.234863281]    2:[1585.94922,1032.28320,1.01250005,0.226562500]C?  3:[429.850769,662.539917,0.850000024,0.204101563]    4:[1122.61023,1329.07703,0.750000000,0.176757813]    5:[926.678284,853.407349,0.725000024,0.199218750]
04-27 07:46:53.883 12610 12610 I MultiTouchBugWorkaroundActivity:             2.164  740/865:                  {0, 1, 2, 3, 4, 5}  0: 1627.43494,585.593323,1.05000007,0.227050781 A?  1: 2377.17456,365.746002,1.18750000,0.234863281     2:[1585.94922,1032.28320,1.01250005,0.226562500]C?  3:[429.850769,662.539917,0.850000024,0.204101563]    4:[1122.61023,1329.07703,0.750000000,0.176757813]    5:[926.678284,853.407349,0.725000024,0.199218750]
04-27 07:46:53.883 12610 12610 I MultiTouchBugWorkaroundActivity:             2.164  741/865:           MOVE   {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.05000007,0.227050781]A?  1:[2377.17456,365.746002,1.18750000,0.234863281]    2: 1957.32043,1226.14844,1.00000000,0.224609375     3:[429.850769,662.539917,0.850000024,0.204101563]    4:[1122.61023,1329.07703,0.750000000,0.176757813]    5:[926.678284,853.407349,0.725000024,0.199218750]
04-27 07:46:53.884 12610 12610 I MultiTouchBugWorkaroundActivity:             2.164  742/865:    POINTER_UP(3) {0, 1, 2, 3, 4, 5}  0:[1627.43494,585.593323,1.05000007,0.227050781]A?  1:[2377.17456,365.746002,1.18750000,0.234863281]    2:[1957.32043,1226.14844,1.00000000,0.224609375]    3:[429.850769,662.539917,0.850000024,0.204101563]    4:[1122.61023,1329.07703,0.750000000,0.176757813]    5:[926.678284,853.407349,0.725000024,0.199218750]
04-27 07:46:53.884 12610 12610 I MultiTouchBugWorkaroundActivity:             2.164  743/865:           MOVE   {0, 1, 2, 4, 5}     0:[1627.43494,585.593323,1.05000007,0.227050781]A?  1:[2377.17456,365.746002,1.18750000,0.234863281]    2: 1585.94922,1032.28320,1.00000000,0.224609375 C?                                                      4: 1101.61755,1345.06592,0.762499988,0.176757813     5:[926.678284,853.407349,0.725000024,0.199218750]
04-27 07:46:53.884 12610 12610 I MultiTouchBugWorkaroundActivity:             2.164  744/865:    POINTER_UP(5) {0, 1, 2, 4, 5}     0:[1627.43494,585.593323,1.05000007,0.227050781]A?  1:[2377.17456,365.746002,1.18750000,0.234863281]    2:[1585.94922,1032.28320,1.00000000,0.224609375]C?                                                      4:[1101.61755,1345.06592,0.762499988,0.176757813]    5:[926.678284,853.407349,0.725000024,0.199218750]
04-27 07:46:53.885 12610 12610 I MultiTouchBugWorkaroundActivity:             2.174  745/865:                  {0, 1, 2, 4}        0: 2030.29504,763.469788,1.05000007,0.227050781     1:[2377.17456,365.746002,1.18750000,0.234863281]    2:[1585.94922,1032.28320,1.00000000,0.224609375]C?                                                      4:[1101.61755,1345.06592,0.762499988,0.176757813]
04-27 07:46:53.885 12610 12610 I MultiTouchBugWorkaroundActivity:             2.174  746/865:                  {0, 1, 2, 4}        0: 1627.43494,585.593323,1.05000007,0.227050781 A?  1: 2428.15698,366.745300,1.16250002,0.233398438     2:[1585.94922,1032.28320,1.00000000,0.224609375]C?                                                      4:[1101.61755,1345.06592,0.762499988,0.176757813]
04-27 07:46:53.885 12610 12610 I MultiTouchBugWorkaroundActivity:             2.174  747/865:                  {0, 1, 2, 4}        0:[1627.43494,585.593323,1.05000007,0.227050781]A?  1:[2428.15698,366.745300,1.16250002,0.233398438]    2: 2003.30444,1227.14783,0.987500012,0.223632813                                                         4:[1101.61755,1345.06592,0.762499988,0.176757813]
04-27 07:46:53.886 12610 12610 I MultiTouchBugWorkaroundActivity:             2.174  748/865:           MOVE   {0, 1, 2, 4}        0:[1627.43494,585.593323,1.05000007,0.227050781]A?  1:[2428.15698,366.745300,1.16250002,0.233398438]    2: 1585.94922,1032.28320,0.987500012,0.223632813 C?                                                      4: 1082.62415,1360.05554,0.762499988,0.176757813
04-27 07:46:53.886 12610 12610 I MultiTouchBugWorkaroundActivity:             2.184  749/865:                  {0, 1, 2, 4}        0: 2079.27808,761.471191,1.05000007,0.227050781     1:[2428.15698,366.745300,1.16250002,0.233398438]    2:[1585.94922,1032.28320,0.987500012,0.223632813]C?                                                      4:[1082.62415,1360.05554,0.762499988,0.176757813]
04-27 07:46:53.886 12610 12610 I MultiTouchBugWorkaroundActivity:             2.184  750/865:                  {0, 1, 2, 4}        0: 1627.43494,585.593323,1.05000007,0.227050781 A?  1: 2479.13916,356.752258,1.13750005,0.232421875     2:[1585.94922,1032.28320,0.987500012,0.223632813]C?                                                      4:[1082.62415,1360.05554,0.762499988,0.176757813]
04-27 07:46:53.887 12610 12610 I MultiTouchBugWorkaroundActivity:             2.184  751/865:                  {0, 1, 2, 4}        0:[1627.43494,585.593323,1.05000007,0.227050781]A?  1:[2479.13916,356.752258,1.13750005,0.232421875]    2: 2047.28918,1220.15271,0.987500012,0.223632813                                                         4:[1082.62415,1360.05554,0.762499988,0.176757813]
04-27 07:46:53.887 12610 12610 I MultiTouchBugWorkaroundActivity:             2.184  752/865:           MOVE   {0, 1, 2, 4}        0:[1627.43494,585.593323,1.05000007,0.227050781]A?  1:[2479.13916,356.752258,1.13750005,0.232421875]    2: 1585.94922,1032.28320,0.987500012,0.223632813 C?                                                      4: 1066.62964,1371.04785,0.775000036,0.175292969
04-27 07:46:53.887 12610 12610 I MultiTouchBugWorkaroundActivity:             2.194  753/865:                  {0, 1, 2, 4}        0: 2120.26392,752.477417,1.05000007,0.227050781     1:[2479.13916,356.752258,1.13750005,0.232421875]    2:[1585.94922,1032.28320,0.987500012,0.223632813]C?                                                      4:[1066.62964,1371.04785,0.775000036,0.175292969]
04-27 07:46:53.888 12610 12610 I MultiTouchBugWorkaroundActivity:             2.194  754/865:                  {0, 1, 2, 4}        0: 1627.43494,585.593323,1.05000007,0.227050781 A?  1: 2526.12305,336.766144,1.10000002,0.232421875     2:[1585.94922,1032.28320,0.987500012,0.223632813]C?                                                      4:[1066.62964,1371.04785,0.775000036,0.175292969]
04-27 07:46:53.888 12610 12610 I MultiTouchBugWorkaroundActivity:             2.194  755/865:                  {0, 1, 2, 4}        0:[1627.43494,585.593323,1.05000007,0.227050781]A?  1:[2526.12305,336.766144,1.10000002,0.232421875]    2: 2087.27539,1206.16235,0.975000024,0.222656250                                                         4:[1066.62964,1371.04785,0.775000036,0.175292969]
04-27 07:46:53.888 12610 12610 I MultiTouchBugWorkaroundActivity:             2.194  756/865:           MOVE   {0, 1, 2, 4}        0:[1627.43494,585.593323,1.05000007,0.227050781]A?  1:[2526.12305,336.766144,1.10000002,0.232421875]    2: 1585.94922,1032.28320,0.975000024,0.222656250 C?                                                      4: 1053.63416,1379.04236,0.775000036,0.175292969
04-27 07:46:53.889 12610 12610 I MultiTouchBugWorkaroundActivity:             2.202  757/865:                  {0, 1, 2, 4}        0: 2156.25146,735.489258,1.05000007,0.228027344     1:[2526.12305,336.766144,1.10000002,0.232421875]    2:[1585.94922,1032.28320,0.975000024,0.222656250]C?                                                      4:[1053.63416,1379.04236,0.775000036,0.175292969]
04-27 07:46:53.889 12610 12610 I MultiTouchBugWorkaroundActivity:             2.202  758/865:                  {0, 1, 2, 4}        0: 1627.43494,585.593323,1.05000007,0.228027344 A?  1: 2541.11768,320.777222,1.10000002,0.232421875     2:[1585.94922,1032.28320,0.975000024,0.222656250]C?                                                      4:[1053.63416,1379.04236,0.775000036,0.175292969]
04-27 07:46:53.889 12610 12610 I MultiTouchBugWorkaroundActivity:             2.202  759/865:                  {0, 1, 2, 4}        0:[1627.43494,585.593323,1.05000007,0.228027344]A?  1:[2541.11768,320.777222,1.10000002,0.232421875]    2: 2125.26221,1185.17700,0.975000024,0.222167969                                                         4:[1053.63416,1379.04236,0.775000036,0.175292969]
04-27 07:46:53.890 12610 12610 I MultiTouchBugWorkaroundActivity:             2.202  760/865:                  {0, 1, 2, 4}        0:[1627.43494,585.593323,1.05000007,0.228027344]A?  1:[2541.11768,320.777222,1.10000002,0.232421875]    2: 1585.94922,1032.28320,0.975000024,0.222167969 C?                                                      4: 1044.63733,1383.03955,0.775000036,0.175292969
04-27 07:46:53.890 12610 12610 I MultiTouchBugWorkaroundActivity:             2.211  761/865:           MOVE   {0, 1, 2, 4}        0: 2186.24097,714.503784,1.06250000,0.227050781     1:[2541.11768,320.777222,1.10000002,0.232421875]    2:[1585.94922,1032.28320,0.975000024,0.222167969]C?                                                      4:[1044.63733,1383.03955,0.775000036,0.175292969]
04-27 07:46:53.890 12610 12610 I MultiTouchBugWorkaroundActivity:             2.211  762/865:    POINTER_UP(1) {0, 1, 2, 4}        0:[2186.24097,714.503784,1.06250000,0.227050781]    1:[2541.11768,320.777222,1.10000002,0.232421875]    2:[1585.94922,1032.28320,0.975000024,0.222167969]C?                                                      4:[1044.63733,1383.03955,0.775000036,0.175292969]
04-27 07:46:53.891 12610 12610 I MultiTouchBugWorkaroundActivity:             2.211  763/865:                  {0, 2, 4}           0: 1627.43494,585.593323,1.06250000,0.227050781 A?                                                      2: 2155.25171,1161.19360,0.975000024,0.221191406                                                         4:[1044.63733,1383.03955,0.775000036,0.175292969]
04-27 07:46:53.891 12610 12610 I MultiTouchBugWorkaroundActivity:             2.211  764/865:           MOVE   {0, 2, 4}           0:[1627.43494,585.593323,1.06250000,0.227050781]A?                                                      2: 1585.94922,1032.28320,0.975000024,0.221191406 C?                                                      4: 1037.63977,1384.03882,0.787500024,0.175292969
04-27 07:46:53.891 12610 12610 I MultiTouchBugWorkaroundActivity:             2.219  765/865:                  {0, 2, 4}           0: 2209.23291,690.520447,1.06250000,0.228027344                                                         2:[1585.94922,1032.28320,0.975000024,0.221191406]C?                                                      4:[1037.63977,1384.03882,0.787500024,0.175292969]
04-27 07:46:53.891 12610 12610 I MultiTouchBugWorkaroundActivity:             2.219  766/865:                  {0, 2, 4}           0: 1627.43494,585.593323,1.06250000,0.228027344 A?                                                      2: 2179.24341,1134.21228,0.975000024,0.221191406                                                         4:[1037.63977,1384.03882,0.787500024,0.175292969]
04-27 07:46:53.892 12610 12610 I MultiTouchBugWorkaroundActivity:             2.219  767/865:           MOVE   {0, 2, 4}           0:[1627.43494,585.593323,1.06250000,0.228027344]A?                                                      2: 1585.94922,1032.28320,0.975000024,0.221191406 C?                                                      4: 1034.64075,1382.04028,0.787500024,0.175292969
04-27 07:46:53.892 12610 12610 I MultiTouchBugWorkaroundActivity:             2.228  768/865:                  {0, 2, 4}           0: 2224.22778,664.538513,1.06250000,0.228027344                                                         2:[1585.94922,1032.28320,0.975000024,0.221191406]C?                                                      4:[1034.64075,1382.04028,0.787500024,0.175292969]
04-27 07:46:53.892 12610 12610 I MultiTouchBugWorkaroundActivity:             2.228  769/865:                  {0, 2, 4}           0: 1627.43494,585.593323,1.06250000,0.228027344 A?                                                      2: 2196.23755,1107.23108,0.975000024,0.221191406                                                         4:[1034.64075,1382.04028,0.787500024,0.175292969]
04-27 07:46:53.892 12610 12610 I MultiTouchBugWorkaroundActivity:             2.228  770/865:           MOVE   {0, 2, 4}           0:[1627.43494,585.593323,1.06250000,0.228027344]A?                                                      2: 1585.94922,1032.28320,0.975000024,0.221191406 C?                                                      4: 1033.64111,1379.04236,0.800000012,0.175292969
04-27 07:46:53.893 12610 12610 I MultiTouchBugWorkaroundActivity:             2.237  771/865:                  {0, 2, 4}           0: 2233.22461,641.554443,1.06250000,0.229003906                                                         2:[1585.94922,1032.28320,0.975000024,0.221191406]C?                                                      4:[1033.64111,1379.04236,0.800000012,0.175292969]
04-27 07:46:53.894 12610 12610 I MultiTouchBugWorkaroundActivity:             2.237  772/865:                  {0, 2, 4}           0: 1627.43494,585.593323,1.06250000,0.229003906 A?                                                      2: 2204.23462,1080.24976,0.975000024,0.221191406                                                         4:[1033.64111,1379.04236,0.800000012,0.175292969]
04-27 07:46:53.895 12610 12610 I MultiTouchBugWorkaroundActivity:             2.237  773/865:           MOVE   {0, 2, 4}           0:[1627.43494,585.593323,1.06250000,0.229003906]A?                                                      2: 1585.94922,1032.28320,0.975000024,0.221191406 C?                                                      4: 1033.64111,1373.04651,0.812500000,0.177246094
04-27 07:46:53.895 12610 12610 I MultiTouchBugWorkaroundActivity:             2.247  774/865:                  {0, 2, 4}           0: 2236.22363,618.570435,1.06250000,0.229003906                                                         2:[1585.94922,1032.28320,0.975000024,0.221191406]C?                                                      4:[1033.64111,1373.04651,0.812500000,0.177246094]
04-27 07:46:53.895 12610 12610 I MultiTouchBugWorkaroundActivity:             2.247  775/865:                  {0, 2, 4}           0: 1627.43494,585.593323,1.06250000,0.229003906 A?                                                      2: 2207.23364,1056.26648,0.975000024,0.221191406                                                         4:[1033.64111,1373.04651,0.812500000,0.177246094]
04-27 07:46:53.896 12610 12610 I MultiTouchBugWorkaroundActivity:             2.247  776/865:                  {0, 2, 4}           0:[1627.43494,585.593323,1.06250000,0.229003906]A?                                                      2: 1585.94922,1032.28320,0.975000024,0.221191406 C?                                                      4: 1033.64111,1368.04993,0.824999988,0.177246094
04-27 07:46:53.896 12610 12610 I MultiTouchBugWorkaroundActivity:             2.257  777/865:                  {0, 2, 4}           0: 2232.22485,596.585693,1.06250000,0.229003906                                                         2:[1585.94922,1032.28320,0.975000024,0.221191406]C?                                                      4:[1033.64111,1368.04993,0.824999988,0.177246094]
04-27 07:46:53.896 12610 12610 I MultiTouchBugWorkaroundActivity:             2.257  778/865:                  {0, 2, 4}           0: 1627.43494,585.593323,1.06250000,0.229003906 A?                                                      2: 2201.23584,1032.28308,0.987500012,0.222167969                                                         4:[1033.64111,1368.04993,0.824999988,0.177246094]
04-27 07:46:53.897 12610 12610 I MultiTouchBugWorkaroundActivity:             2.257  779/865:           MOVE   {0, 2, 4}           0:[1627.43494,585.593323,1.06250000,0.229003906]A?                                                      2: 1585.94922,1032.28320,0.987500012,0.222167969 C?                                                      4: 1035.64038,1362.05408,0.837500036,0.179687500
04-27 07:46:53.897 12610 12610 I MultiTouchBugWorkaroundActivity:             2.267  780/865:                  {0, 2, 4}           0: 2222.22852,571.603027,1.03750002,0.227539063                                                         2:[1585.94922,1032.28320,0.987500012,0.222167969]C?                                                      4:[1035.64038,1362.05408,0.837500036,0.179687500]
04-27 07:46:53.897 12610 12610 I MultiTouchBugWorkaroundActivity:             2.267  781/865:                  {0, 2, 4}           0: 1627.43494,585.593323,1.03750002,0.227539063 A?                                                      2: 2192.23877,1013.29633,0.987500012,0.222656250                                                         4:[1035.64038,1362.05408,0.837500036,0.179687500]
04-27 07:46:53.898 12610 12610 I MultiTouchBugWorkaroundActivity:             2.267  782/865:                  {0, 2, 4}           0:[1627.43494,585.593323,1.03750002,0.227539063]A?                                                      2: 1585.94922,1032.28320,0.987500012,0.222656250 C?                                                      4: 1038.63940,1357.05762,0.837500036,0.180664063
04-27 07:46:53.898 12610 12610 I MultiTouchBugWorkaroundActivity:             2.277  783/865:           MOVE   {0, 2, 4}           0: 2209.23291,546.620422,1.01250005,0.224609375                                                         2:[1585.94922,1032.28320,0.987500012,0.222656250]C?                                                      4:[1038.63940,1357.05762,0.837500036,0.180664063]
04-27 07:46:53.898 12610 12610 I MultiTouchBugWorkaroundActivity:             2.277  784/865:  POINTER_DOWN(1) {0, 1, 2, 4}        0:[2209.23291,546.620422,1.01250005,0.224609375]    1: 2597.09839,107.925049,0.900000036,0.196777344     2:[1585.94922,1032.28320,0.987500012,0.222656250]C?                                                      4:[1038.63940,1357.05762,0.837500036,0.180664063]
04-27 07:46:53.901 12610 12610 I MultiTouchBugWorkaroundActivity:             2.277  785/865:                  {0, 1, 2, 4}        0: 1627.43494,585.593323,1.01250005,0.224609375 A?  1:[2597.09839,107.925049,0.900000036,0.196777344]    2: 2174.24512,995.308777,1.00000000,0.229003906                                                         4:[1038.63940,1357.05762,0.837500036,0.180664063]
04-27 07:46:53.901 12610 12610 I MultiTouchBugWorkaroundActivity:             2.277  786/865:           MOVE   {0, 1, 2, 4}        0:[1627.43494,585.593323,1.01250005,0.224609375]A?  1:[2597.09839,107.925049,0.900000036,0.196777344]    2: 1585.94922,1032.28320,1.00000000,0.229003906 C?                                                      4: 1043.63770,1351.06177,0.850000024,0.182128906
04-27 07:46:53.901 12610 12610 I MultiTouchBugWorkaroundActivity:             2.287  787/865:                  {0, 1, 2, 4}        0: 2189.23999,520.638428,0.975000024,0.223632813     1:[2597.09839,107.925049,0.900000036,0.196777344]    2:[1585.94922,1032.28320,1.00000000,0.229003906]C?                                                      4:[1043.63770,1351.06177,0.850000024,0.182128906]
04-27 07:46:53.902 12610 12610 I MultiTouchBugWorkaroundActivity:             2.287  788/865:                  {0, 1, 2, 4}        0: 1627.43494,585.593323,0.975000024,0.223632813 A?  1:[2597.09839,107.925049,0.900000036,0.196777344]    2:[1585.94922,1032.28320,1.00000000,0.229003906]C?                                                      4:[1043.63770,1351.06177,0.850000024,0.182128906]
04-27 07:46:53.902 12610 12610 I MultiTouchBugWorkaroundActivity:             2.287  789/865:                  {0, 1, 2, 4}        0:[1627.43494,585.593323,0.975000024,0.223632813]A?  1:[2597.09839,107.925049,0.900000036,0.196777344]    2: 2153.25244,980.319214,1.01250005,0.229003906                                                         4:[1043.63770,1351.06177,0.850000024,0.182128906]
04-27 07:46:53.902 12610 12610 I MultiTouchBugWorkaroundActivity:             2.287  790/865:           MOVE   {0, 1, 2, 4}        0:[1627.43494,585.593323,0.975000024,0.223632813]A?  1:[2597.09839,107.925049,0.900000036,0.196777344]    2: 1585.94922,1032.28320,1.01250005,0.229003906 C?                                                      4: 1050.63525,1347.06458,0.850000024,0.182128906
04-27 07:46:53.903 12610 12610 I MultiTouchBugWorkaroundActivity:             2.297  791/865:    POINTER_UP(0) {0, 1, 2, 4}        0:[1627.43494,585.593323,0.975000024,0.223632813]A?  1:[2597.09839,107.925049,0.900000036,0.196777344]    2:[1585.94922,1032.28320,1.01250005,0.229003906]C?                                                      4:[1050.63525,1347.06458,0.850000024,0.182128906]
04-27 07:46:53.903 12610 12610 I MultiTouchBugWorkaroundActivity:             2.297  792/865:                  {1, 2, 4}                                                               1:(2597.09839,107.925049,0.875000000,0.196777344)    2:[1585.94922,1032.28320,1.01250005,0.229003906]C?                                                      4:[1050.63525,1347.06458,0.850000024,0.182128906]
04-27 07:46:53.903 12610 12610 I MultiTouchBugWorkaroundActivity:             2.297  793/865:                  {1, 2, 4}                                                               1:[2597.09839,107.925049,0.875000000,0.196777344]    2: 2125.26221,965.329590,1.01250005,0.229003906                                                         4:[1050.63525,1347.06458,0.850000024,0.182128906]
04-27 07:46:53.903 12610 12610 I MultiTouchBugWorkaroundActivity:             2.297  794/865:           MOVE   {1, 2, 4}                                                               1:[2597.09839,107.925049,0.875000000,0.196777344]    2:[2125.26221,965.329590,1.01250005,0.229003906]                                                        4: 1062.63110,1340.06934,0.837500036,0.183105469
04-27 07:46:53.904 12610 12610 I MultiTouchBugWorkaroundActivity:             2.308  795/865:    POINTER_UP(1) {1, 2, 4}                                                               1:[2597.09839,107.925049,0.875000000,0.196777344]    2:[2125.26221,965.329590,1.01250005,0.229003906]                                                        4:[1062.63110,1340.06934,0.837500036,0.183105469]
04-27 07:46:53.904 12610 12610 I MultiTouchBugWorkaroundActivity:             2.308  796/865:                  {2, 4}                                                                                                                      2: 2095.27246,954.337280,1.01250005,0.229003906                                                         4:[1062.63110,1340.06934,0.837500036,0.183105469]
04-27 07:46:53.904 12610 12610 I MultiTouchBugWorkaroundActivity:             2.308  797/865:           MOVE   {2, 4}                                                                                                                      2:[2095.27246,954.337280,1.01250005,0.229003906]                                                        4: 1076.62622,1331.07568,0.812500000,0.185546875
04-27 07:46:53.904 12610 12610 I MultiTouchBugWorkaroundActivity:             2.318  798/865:           MOVE   {2, 4}                                                                                                                      2: 2059.28491,945.343506,1.01250005,0.227050781                                                         4:[1076.62622,1331.07568,0.812500000,0.185546875]
*/

/*
and another...
0.577  128/747:           MOVE   {0}          0: 1958.75586,635.934753,0.987500012,0.218261719
0.580  129/747:                  {0}          0: 1948.32349,631.561401,1.00000000,0.218261719 A
0.584  130/747:           MOVE   {0}          0: 1932.82886,625.065918,1.00000000,0.218261719
0.589  131/747:  POINTER_DOWN(1) {0,1}        0: 1948.32349,631.561401,1.00000000,0.218261719 A   1: 1744.39441,1032.28308,0.712500036,0.183105469 A
0.589  132/747:                  {0,1}        0: 1918.33398,618.570435,1.00000000,0.219238281 B   1:[1744.39441,1032.28308,0.712500036,0.183105469]A
0.594  133/747:           MOVE   {0,1}        0:[1918.33398,618.570435,1.00000000,0.219238281]B   1:[1744.39441,1032.28308,0.712500036,0.183105469]A
0.597  134/747:                  {0,1}        0:[1918.33398,618.570435,1.00000000,0.219238281]B   1:(1744.39441,1032.28308,0.737500012,0.187988281)A
0.597  135/747:           MOVE   {0,1}        0: 1889.34399,606.578735,1.01250005,0.219238281     1:[1744.39441,1032.28308,0.737500012,0.187988281]A
0.606  136/747:                  {0,1}        0: 1918.33398,618.570435,1.01250005,0.219238281 B   1:(1744.39441,1032.28308,0.750000000,0.192871094)A
0.606  137/747:           MOVE   {0,1}        0: 1862.35339,595.586365,1.01250005,0.221191406     1:[1744.39441,1032.28308,0.750000000,0.192871094]A
0.614  138/747:                  {0,1}        0: 1918.33398,618.570435,1.01250005,0.221191406 B   1:(1744.39441,1032.28308,0.775000036,0.198730469)A
0.614  139/747:           MOVE   {0,1}        0: 1837.36206,585.593323,1.01250005,0.220214844     1:[1744.39441,1032.28308,0.775000036,0.198730469]A
0.623  140/747:                  {0,1}        0: 1918.33398,618.570435,1.01250005,0.220214844 B   1: 1737.39673,1028.28589,0.787500024,0.200683594
0.623  141/747:           MOVE   {0,1}        0: 1814.37000,577.598877,1.01250005,0.220214844     1: 1744.39441,1032.28308,0.787500024,0.200683594 A
0.632  142/747:                  {0,1}        0: 1918.33398,618.570435,1.01250005,0.220214844 B   1: 1721.40234,1021.29077,0.812500000,0.206054688
0.632  143/747:           MOVE   {0,1}        0: 1793.37732,569.604431,1.02499998,0.221191406     1: 1744.39441,1032.28308,0.812500000,0.206054688 A
0.641  144/747:                  {0,1}        0: 1918.33398,618.570435,1.02499998,0.221191406 B   1: 1705.40784,1014.29559,0.824999988,0.210449219
0.641  145/747:           MOVE   {0,1}        0: 1773.38428,562.609314,1.02499998,0.221191406     1: 1744.39441,1032.28308,0.824999988,0.210449219 A
0.649  146/747:                  {0,1}        0: 1918.33398,618.570435,1.02499998,0.221191406 B   1: 1687.41418,1005.30188,0.850000024,0.211914063
0.649  147/747:                  {0,1}        0: 1754.39087,556.613464,1.02499998,0.221191406     1: 1744.39441,1032.28308,0.850000024,0.211914063 A
0.659  148/747:                  {0,1}        0: 1918.33398,618.570435,1.02499998,0.221191406 B   1: 1667.42102,996.308105,0.862500012,0.212402344
0.659  149/747:           MOVE   {0,1}        0: 1735.39746,551.616943,1.02499998,0.222167969     1: 1744.39441,1032.28308,0.862500012,0.212402344 A
0.667  150/747:                  {0,1}        0: 1918.33398,618.570435,1.02499998,0.222167969 B   1: 1645.42871,986.315063,0.875000000,0.214355469
0.667  151/747:                  {0,1}        0: 1717.40369,546.620422,1.02499998,0.222167969     1: 1744.39441,1032.28308,0.875000000,0.214355469 A
0.676  152/747:                  {0,1}        0: 1918.33398,618.570435,1.02499998,0.222167969 B   1: 1623.43640,976.321960,0.887499988,0.216308594
0.676  153/747:           MOVE   {0,1}        0: 1699.40991,541.623840,1.02499998,0.223144531     1: 1744.39441,1032.28308,0.887499988,0.216308594 A
0.676  154/747:  POINTER_DOWN(2) {0,1,2}      0:[1699.40991,541.623840,1.02499998,0.223144531]    1:[1744.39441,1032.28308,0.887499988,0.216308594]A   2: 1214.57825,1078.25122,0.875000000,0.199218750
0.685  155/747:                  {0,1,2}      0: 1918.33398,618.570435,1.02499998,0.223144531 B   1: 1603.44324,967.328247,0.887499988,0.216308594     2:[1214.57825,1078.25122,0.875000000,0.199218750]
0.685  156/747:                  {0,1,2}      0: 1682.41589,535.628052,1.02499998,0.223144531     1: 1744.39441,1032.28308,0.887499988,0.216308594 A   2:[1214.57825,1078.25122,0.875000000,0.199218750]
0.685  157/747:                  {0,1,2}      0: 1918.33398,618.570435,1.02499998,0.223144531 B   1:[1744.39441,1032.28308,0.887499988,0.216308594]A   2:(1214.57825,1078.25122,0.875000000,0.200195313)
0.694  158/747:                  {0,1,2}      0:[1918.33398,618.570435,1.02499998,0.223144531]B   1: 1584.44983,958.334473,0.900000036,0.216308594     2:[1214.57825,1078.25122,0.875000000,0.200195313]
0.694  159/747:                  {0,1,2}      0: 1666.42139,530.631470,1.02499998,0.223144531     1: 1744.39441,1032.28308,0.900000036,0.216308594 A   2:[1214.57825,1078.25122,0.875000000,0.200195313]
0.694  160/747:           MOVE   {0,1,2}      0: 1918.33398,618.570435,1.02499998,0.223144531 B   1:[1744.39441,1032.28308,0.900000036,0.216308594]A   2:(1214.57825,1078.25122,0.887499988,0.201660156)
0.703  161/747:                  {0,1,2}      0:[1918.33398,618.570435,1.02499998,0.223144531]B   1: 1568.45544,951.339355,0.912500024,0.216308594     2:[1214.57825,1078.25122,0.887499988,0.201660156]
0.703  162/747:                  {0,1,2}      0: 1651.42664,524.635681,1.03750002,0.223144531     1: 1744.39441,1032.28308,0.912500024,0.216308594 A   2:[1214.57825,1078.25122,0.887499988,0.201660156]
0.703  163/747:           MOVE   {0,1,2}      0: 1918.33398,618.570435,1.03750002,0.223144531 B   1:[1744.39441,1032.28308,0.912500024,0.216308594]A   2:(1214.57825,1078.25122,0.887499988,0.203613281)
0.711  164/747:                  {0,1,2}      0:[1918.33398,618.570435,1.03750002,0.223144531]B   1: 1553.46069,946.342773,0.912500024,0.218261719     2:[1214.57825,1078.25122,0.887499988,0.203613281]
0.712  165/747:                  {0,1,2}      0: 1637.43152,518.639832,1.03750002,0.223144531     1: 1744.39441,1032.28308,0.912500024,0.218261719 A   2:[1214.57825,1078.25122,0.887499988,0.203613281]
0.712  166/747:           MOVE   {0,1,2}      0: 1918.33398,618.570435,1.03750002,0.223144531 B   1:[1744.39441,1032.28308,0.912500024,0.218261719]A   2:[1214.57825,1078.25122,0.887499988,0.203613281]
0.720  167/747:                  {0,1,2}      0:[1918.33398,618.570435,1.03750002,0.223144531]B   1: 1539.46545,943.344910,0.925000012,0.219238281     2:[1214.57825,1078.25122,0.887499988,0.203613281]
0.720  168/747:                  {0,1,2}      0: 1623.43640,513.643311,1.03750002,0.224121094     1: 1744.39441,1032.28308,0.925000012,0.219238281 A   2:[1214.57825,1078.25122,0.887499988,0.203613281]
0.720  169/747:           MOVE   {0,1,2}      0: 1918.33398,618.570435,1.03750002,0.224121094 B   1:[1744.39441,1032.28308,0.925000012,0.219238281]A   2:(1214.57825,1078.25122,0.875000000,0.202636719)
0.729  170/747:                  {0,1,2}      0:[1918.33398,618.570435,1.03750002,0.224121094]B   1: 1524.47070,943.344910,0.925000012,0.219238281     2:[1214.57825,1078.25122,0.875000000,0.202636719]
0.729  171/747:                  {0,1,2}      0: 1610.44092,508.646759,1.03750002,0.224609375     1: 1744.39441,1032.28308,0.925000012,0.219238281 A   2:[1214.57825,1078.25122,0.875000000,0.202636719]
0.729  172/747:           MOVE   {0,1,2}      0: 1918.33398,618.570435,1.03750002,0.224609375 B   1:[1744.39441,1032.28308,0.925000012,0.219238281]A   2:(1214.57825,1078.25122,0.850000024,0.199218750)
0.737  173/747:                  {0,1,2}      0:[1918.33398,618.570435,1.03750002,0.224609375]B   1: 1512.47485,946.342773,0.925000012,0.219238281     2:[1214.57825,1078.25122,0.850000024,0.199218750]
0.738  174/747:           MOVE   {0,1,2}      0: 1599.44470,503.650238,1.05000007,0.224609375     1: 1744.39441,1032.28308,0.925000012,0.219238281 A   2:[1214.57825,1078.25122,0.850000024,0.199218750]
0.738  175/747:    POINTER_UP(2) {0,1,2}      0:[1599.44470,503.650238,1.05000007,0.224609375]    1:[1744.39441,1032.28308,0.925000012,0.219238281]A   2:[1214.57825,1078.25122,0.850000024,0.199218750]
0.745  176/747:                  {0,1}        0: 1918.33398,618.570435,1.05000007,0.224609375 B   1: 1500.47900,951.339355,0.912500024,0.219238281
0.745  177/747:           MOVE   {0,1}        0: 1588.44849,500.652313,1.05000007,0.224609375     1: 1744.39441,1032.28308,0.912500024,0.219238281 A
0.753  178/747:                  {0,1}        0: 1918.33398,618.570435,1.05000007,0.224609375 B   1: 1490.48254,955.336548,0.900000036,0.218261719
0.753  179/747:                  {0,1}        0: 1579.45166,497.654388,1.05000007,0.225585938     1: 1744.39441,1032.28308,0.900000036,0.218261719 A
0.761  180/747:                  {0,1}        0: 1918.33398,618.570435,1.05000007,0.225585938 B   1: 1482.48523,955.336548,0.875000000,0.216308594
0.761  181/747:           MOVE   {0,1}        0: 1571.45435,494.656494,1.06250000,0.225585938     1: 1744.39441,1032.28308,0.875000000,0.216308594 A
0.769  182/747:                  {0,1}        0: 1918.33398,618.570435,1.06250000,0.225585938 B   1: 1477.48706,954.337280,0.850000024,0.213378906
0.769  183/747:           MOVE   {0,1}        0: 1564.45679,493.657166,1.06250000,0.225585938     1: 1744.39441,1032.28308,0.850000024,0.213378906 A
0.777  184/747:    POINTER_UP(1) {0,1}        0:[1564.45679,493.657166,1.06250000,0.225585938]    1:[1744.39441,1032.28308,0.850000024,0.213378906]A
0.777  185/747:                  {0}          0: 1559.45862,492.657867,1.06250000,0.225585938
0.781  186/747:           MOVE   {0}          0: 1556.95947,492.158203,1.06250000,0.225585938
0.786  187/747:                  {0}          0: 1556.45959,491.658569,1.06250000,0.225585938
0.793  188/747:                  {0}          0: 1554.46033,490.659271,1.06250000,0.224609375 C
0.794  189/747:           MOVE   {0}          0: 1554.21191,490.535126,1.06250000,0.224609375
0.801  190/747:           MOVE   {0}          0: 1553.46069,490.659271,1.07500005,0.224121094
0.801  191/747:  POINTER_DOWN(1) {0,1}        0:[1553.46069,490.659271,1.07500005,0.224121094]    1: 748.740051,737.487854,1.08749998,0.218261719 A
0.809  192/747:                  {0,1}        0: 1554.46033,490.659271,1.07500005,0.224121094 C   1:[748.740051,737.487854,1.08749998,0.218261719]A
0.814  193/747:           MOVE   {0,1}        0: 1554.96021,490.659271,1.07500005,0.224121094 D   1:[748.740051,737.487854,1.08749998,0.218261719]A
0.818  194/747:                  {0,1}        0: 1555.45996,491.658569,1.07500005,0.224609375     1:[748.740051,737.487854,1.08749998,0.218261719]A
0.826  195/747:           MOVE   {0,1}        0: 1558.45886,492.657867,1.07500005,0.224609375     1:[748.740051,737.487854,1.08749998,0.218261719]A
0.835  196/747:                  {0,1}        0: 1562.45752,493.657166,1.07500005,0.224609375     1:[748.740051,737.487854,1.08749998,0.218261719]A
0.835  197/747:                  {0,1}        0: 1554.96021,490.659271,1.07500005,0.224609375 D   1:(748.740051,737.487854,1.07500005,0.218261719)A
0.843  198/747:           MOVE   {0,1}        0: 1567.45581,494.656494,1.07500005,0.224609375     1:[748.740051,737.487854,1.07500005,0.218261719]A
0.852  199/747:                  {0,1}        0: 1573.45374,496.655090,1.07500005,0.224121094     1:[748.740051,737.487854,1.07500005,0.218261719]A
0.852  200/747:           MOVE   {0,1}        0: 1554.96021,490.659271,1.07500005,0.224121094 D   1:(748.740051,737.487854,1.07500005,0.217285156)A
0.862  201/747:           MOVE   {0,1}        0: 1583.45020,499.653015,1.07500005,0.224121094     1:[748.740051,737.487854,1.07500005,0.217285156]A
0.870  202/747:                  {0,1}        0: 1596.44568,502.650940,1.07500005,0.224121094     1:[748.740051,737.487854,1.07500005,0.217285156]A
0.870  203/747:           MOVE   {0,1}        0: 1554.96021,490.659271,1.07500005,0.224121094 D   1:[748.740051,737.487854,1.07500005,0.217285156]A
0.879  204/747:                  {0,1}        0: 1613.43982,505.648834,1.07500005,0.224121094     1:[748.740051,737.487854,1.07500005,0.217285156]A
0.879  205/747:           MOVE   {0,1}        0: 1554.96021,490.659271,1.07500005,0.224121094 D   1: 747.740356,741.485046,1.06250000,0.216308594
0.888  206/747:                  {0,1}        0: 1633.43286,509.646057,1.07500005,0.224609375     1: 748.740051,737.487854,1.06250000,0.216308594 A
0.888  207/747:           MOVE   {0,1}        0: 1554.96021,490.659271,1.07500005,0.224609375 D   1: 746.740723,744.482971,1.06250000,0.216308594
0.897  208/747:                  {0,1}        0: 1656.42493,514.642578,1.07500005,0.224609375     1: 748.740051,737.487854,1.06250000,0.216308594 A
0.897  209/747:           MOVE   {0,1}        0: 1554.96021,490.659271,1.07500005,0.224609375 D   1: 746.740723,746.481628,1.06250000,0.216308594
0.907  210/747:                  {0,1}        0: 1680.41650,518.639832,1.07500005,0.224609375     1: 748.740051,737.487854,1.06250000,0.216308594 A
0.907  211/747:           MOVE   {0,1}        0: 1554.96021,490.659271,1.07500005,0.224609375 D   1: 746.740723,747.480896,1.06250000,0.215332031
0.916  212/747:                  {0,1}        0: 1706.40759,523.636353,1.07500005,0.224609375     1: 748.740051,737.487854,1.06250000,0.215332031 A
0.917  213/747:           MOVE   {0,1}        0: 1554.96021,490.659271,1.07500005,0.224609375 D   1:(748.740051,737.487854,1.05000007,0.214355469)A
0.927  214/747:  POINTER_DOWN(2) {0,1,2}      0:[1554.96021,490.659271,1.07500005,0.224609375]D   1:[748.740051,737.487854,1.05000007,0.214355469]A   2: 1863.35303,1013.29633,1.01250005,0.223632813
0.927  215/747:           MOVE   {0,1,2}      0: 1733.39819,529.632202,1.06250000,0.224609375     1:[748.740051,737.487854,1.05000007,0.214355469]A   2:[1863.35303,1013.29633,1.01250005,0.223632813]
0.936  216/747:                  {0,1,2}      0: 1758.38953,534.628723,1.06250000,0.224609375     1:[748.740051,737.487854,1.05000007,0.214355469]A   2:[1863.35303,1013.29633,1.01250005,0.223632813]
0.936  217/747:                  {0,1,2}      0: 1554.96021,490.659271,1.06250000,0.224609375 D   1: 746.740723,748.480225,1.05000007,0.214355469 B   2:[1863.35303,1013.29633,1.01250005,0.223632813]
0.946  218/747:           MOVE   {0,1,2}      0: 1783.38086,540.624573,1.06250000,0.224609375     1: 748.740051,737.487854,1.05000007,0.214355469 A   2:[1863.35303,1013.29633,1.01250005,0.223632813]
0.946  219/747:  POINTER_DOWN(3) {0,1,2,3}    0:[1783.38086,540.624573,1.06250000,0.224609375]    1:[748.740051,737.487854,1.05000007,0.214355469]A   2:[1863.35303,1013.29633,1.01250005,0.223632813]    3: 1105.61609,1140.20813,0.612500012,0.206054688
0.956  220/747:                  {0,1,2,3}    0: 1808.37219,546.620422,1.06250000,0.224121094     1:[748.740051,737.487854,1.05000007,0.214355469]A   2:[1863.35303,1013.29633,1.01250005,0.223632813]    3:[1105.61609,1140.20813,0.612500012,0.206054688]
0.956  221/747:                  {0,1,2,3}    0: 1554.96021,490.659271,1.06250000,0.224121094 D   1:[748.740051,737.487854,1.05000007,0.214355469]A   2:[1863.35303,1013.29633,1.01250005,0.223632813]    3:[1105.61609,1140.20813,0.612500012,0.206054688]
0.956  222/747:                  {0,1,2,3}    0:[1554.96021,490.659271,1.06250000,0.224121094]D   1:[748.740051,737.487854,1.05000007,0.214355469]A   2:[1863.35303,1013.29633,1.01250005,0.223632813]    3:(1105.61609,1140.20813,0.637499988,0.205078125)
0.966  223/747:                  {0,1,2,3}    0:[1554.96021,490.659271,1.06250000,0.224121094]D   1:[748.740051,737.487854,1.05000007,0.214355469]A   2: 1875.34888,1017.29352,1.01250005,0.223632813     3:[1105.61609,1140.20813,0.637499988,0.205078125]
0.966  224/747:                  {0,1,2,3}    0: 1834.36316,554.614868,1.05000007,0.224121094     1:[748.740051,737.487854,1.05000007,0.214355469]A   2:[1875.34888,1017.29352,1.01250005,0.223632813]    3:[1105.61609,1140.20813,0.637499988,0.205078125]
0.966  225/747:                  {0,1,2,3}    0: 1554.96021,490.659271,1.05000007,0.224121094 D   1:[748.740051,737.487854,1.05000007,0.214355469]A   2:[1875.34888,1017.29352,1.01250005,0.223632813]    3:[1105.61609,1140.20813,0.637499988,0.205078125]
0.966  226/747:           MOVE   {0,1,2,3}    0:[1554.96021,490.659271,1.05000007,0.224121094]D   1:[748.740051,737.487854,1.05000007,0.214355469]A   2:[1875.34888,1017.29352,1.01250005,0.223632813]    3:(1105.61609,1140.20813,0.662500024,0.208007813)
0.977  227/747:                  {0,1,2,3}    0:[1554.96021,490.659271,1.05000007,0.224121094]D   1:[748.740051,737.487854,1.05000007,0.214355469]A   2: 1899.34058,1029.28516,1.01250005,0.223632813     3:[1105.61609,1140.20813,0.662500024,0.208007813]
0.977  228/747:                  {0,1,2,3}    0: 1860.35413,562.609314,1.05000007,0.223144531     1:[748.740051,737.487854,1.05000007,0.214355469]A   2:[1899.34058,1029.28516,1.01250005,0.223632813]    3:[1105.61609,1140.20813,0.662500024,0.208007813]
0.977  229/747:           MOVE   {0,1,2,3}    0: 1554.96021,490.659271,1.05000007,0.223144531 D   1:[748.740051,737.487854,1.05000007,0.214355469]A   2:[1899.34058,1029.28516,1.01250005,0.223632813]    3:(1105.61609,1140.20813,0.687500000,0.208984375)
0.986  230/747:                  {0,1,2,3}    0:[1554.96021,490.659271,1.05000007,0.223144531]D   1:[748.740051,737.487854,1.05000007,0.214355469]A   2: 1927.33081,1041.27686,1.00000000,0.223632813     3:[1105.61609,1140.20813,0.687500000,0.208984375]
0.987  231/747:                  {0,1,2,3}    0: 1886.34509,571.603027,1.03750002,0.223144531     1:[748.740051,737.487854,1.05000007,0.214355469]A   2:[1927.33081,1041.27686,1.00000000,0.223632813]    3:[1105.61609,1140.20813,0.687500000,0.208984375]
0.987  232/747:                  {0,1,2,3}    0: 1554.96021,490.659271,1.03750002,0.223144531 D   1: 745.741089,748.480225,1.03750002,0.213378906     2:[1927.33081,1041.27686,1.00000000,0.223632813]    3:[1105.61609,1140.20813,0.687500000,0.208984375]
0.987  233/747:                  {0,1,2,3}    0:[1554.96021,490.659271,1.03750002,0.223144531]D   1: 748.740051,737.487854,1.03750002,0.213378906 A   2:[1927.33081,1041.27686,1.00000000,0.223632813]    3:(1105.61609,1140.20813,0.712500036,0.210937500)
0.996  234/747:                  {0,1,2,3}    0:[1554.96021,490.659271,1.03750002,0.223144531]D   1:[748.740051,737.487854,1.03750002,0.213378906]A   2: 1962.31873,1056.26648,1.00000000,0.224609375     3:[1105.61609,1140.20813,0.712500036,0.210937500]
0.996  235/747:                  {0,1,2,3}    0: 1913.33569,582.595398,1.02499998,0.221191406     1:[748.740051,737.487854,1.03750002,0.213378906]A   2:[1962.31873,1056.26648,1.00000000,0.224609375]    3:[1105.61609,1140.20813,0.712500036,0.210937500]
0.996  236/747:                  {0,1,2,3}    0: 1554.96021,490.659271,1.02499998,0.221191406 D   1:(748.740051,737.487854,1.02499998,0.214355469)A   2:[1962.31873,1056.26648,1.00000000,0.224609375]    3:[1105.61609,1140.20813,0.712500036,0.210937500]
0.996  237/747:           MOVE   {0,1,2,3}    0:[1554.96021,490.659271,1.02499998,0.221191406]D   1:[748.740051,737.487854,1.02499998,0.214355469]A   2:[1962.31873,1056.26648,1.00000000,0.224609375]    3:(1105.61609,1140.20813,0.737500012,0.211914063)
1.006  238/747:                  {0,1,2,3}    0:[1554.96021,490.659271,1.02499998,0.221191406]D   1:[748.740051,737.487854,1.02499998,0.214355469]A   2: 1999.30579,1072.25537,1.00000000,0.225585938     3:[1105.61609,1140.20813,0.737500012,0.211914063]
1.006  239/747:                  {0,1,2,3}    0: 1944.32495,597.585022,1.00000000,0.219238281     1:[748.740051,737.487854,1.02499998,0.214355469]A   2:[1999.30579,1072.25537,1.00000000,0.225585938]    3:[1105.61609,1140.20813,0.737500012,0.211914063]
1.006  240/747:                  {0,1,2,3}    0: 1554.96021,490.659271,1.00000000,0.219238281 D   1: 746.740723,748.480225,0.987500012,0.214355469 B   2:[1999.30579,1072.25537,1.00000000,0.225585938]    3:[1105.61609,1140.20813,0.737500012,0.211914063]
1.006  241/747:                  {0,1,2,3}    0:[1554.96021,490.659271,1.00000000,0.219238281]D   1: 748.740051,737.487854,0.987500012,0.214355469 A   2:[1999.30579,1072.25537,1.00000000,0.225585938]    3:(1105.61609,1140.20813,0.762499988,0.212402344)
1.015  242/747:                  {0,1,2,3}    0:[1554.96021,490.659271,1.00000000,0.219238281]D   1:[748.740051,737.487854,0.987500012,0.214355469]A   2: 2037.29260,1090.24292,1.00000000,0.235351563     3:[1105.61609,1140.20813,0.762499988,0.212402344]
1.015  243/747:           MOVE   {0,1,2,3}    0: 1980.31238,621.568359,0.962500036,0.218261719     1:[748.740051,737.487854,0.987500012,0.214355469]A   2:[2037.29260,1090.24292,1.00000000,0.235351563]    3:[1105.61609,1140.20813,0.762499988,0.212402344]
1.015  244/747:    POINTER_UP(1) {0,1,2,3}    0:[1980.31238,621.568359,0.962500036,0.218261719]    1:[748.740051,737.487854,0.987500012,0.214355469]A   2:[2037.29260,1090.24292,1.00000000,0.235351563]    3:[1105.61609,1140.20813,0.762499988,0.212402344]
1.015  245/747:           MOVE   {0,2,3}      0: 1554.96021,490.659271,0.962500036,0.218261719 D                                                       2:[2037.29260,1090.24292,1.00000000,0.235351563]    3:(1105.61609,1140.20813,0.787500024,0.213378906)
1.022  246/747:           MOVE   {0,2,3}      0:[1554.96021,490.659271,0.962500036,0.218261719]D                                                       2: 2074.27979,1108.23035,0.987500012,0.233398438     3:[1105.61609,1140.20813,0.787500024,0.213378906]
1.022  247/747:    POINTER_UP(0) {0,2,3}      0:[1554.96021,490.659271,0.962500036,0.218261719]D                                                       2:[2074.27979,1108.23035,0.987500012,0.233398438]    3:[1105.61609,1140.20813,0.787500024,0.213378906]
1.022  248/747:           MOVE   {2,3}                                                                                                                2:[2074.27979,1108.23035,0.987500012,0.233398438]    3:(1105.61609,1140.20813,0.800000012,0.214355469)
1.030  249/747:                  {2,3}                                                                                                                2: 2107.26831,1127.21716,0.987500012,0.233886719     3:[1105.61609,1140.20813,0.800000012,0.214355469]
*/

