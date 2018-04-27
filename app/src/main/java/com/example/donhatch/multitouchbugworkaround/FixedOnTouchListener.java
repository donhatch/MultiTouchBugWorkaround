// The infamous Oreo 8.1 multitouch bug
//
// OH WHOA, here is it happening triggered by POINTER_DOWN(1) instead of POINTER_DOWN(0)!
// and *only* pointer 0 is bugging, not pointer 1!  All right, need to think about that.
// Argh, and I guess it's not properly grouping the 1's, either.
// I think I need to to write a parse/parseDump/undump.
/*
0.189   28/217:                  {0}     0: 2241.22192,512.643982,1.27499998,0.246582031
0.194   29/217:           MOVE   {0}     0: 2250.21875,505.648834,1.27499998,0.246582031
0.198   30/217:           MOVE   {0}     0: 2260.21533,498.653717,1.27499998,0.246582031
0.206   31/217:  POINTER_DOWN(1) {0, 1}  0:[2260.21533,498.653717,1.27499998,0.246582031]    1: 976.660889,614.573181,1.87500000,0.322265625    <- should be A
0.206   32/217:                  {0, 1}  0: 2279.20874,484.663422,1.28750002,0.246582031     1:[976.660889,614.573181,1.87500000,0.322265625]    <- should be A
0.210   33/217:           MOVE   {0, 1}  0: 2288.70557,477.668274,1.28750002,0.246582031 A   1:[976.660889,614.573181,1.87500000,0.322265625]    <- should be A
0.214   34/217:                  {0, 1}  0:[2288.70557,477.668274,1.28750002,0.246582031]A   1:(976.660889,614.573181,1.85000002,0.317382813)    <- should be A
0.214   35/217:           MOVE   {0, 1}  0: 2297.20239,471.672455,1.28750002,0.247558594     1:[976.660889,614.573181,1.85000002,0.317382813]    <- should be A
0.223   36/217:                  {0, 1}  0: 2288.70557,477.668274,1.28750002,0.247558594 A   1:(976.660889,614.573181,1.81250000,0.311523438)    <- should be A
0.223   37/217:           MOVE   {0, 1}  0: 2315.19629,459.680756,1.30000007,0.247558594     1:[976.660889,614.573181,1.81250000,0.311523438]    <- should be A
0.231   38/217:                  {0, 1}  0: 2288.70557,477.668274,1.30000007,0.247558594 A   1:(976.660889,614.573181,1.77499998,0.309570313)    <- should be A
0.231   39/217:           MOVE   {0, 1}  0: 2332.19019,448.688416,1.30000007,0.248535156     1:[976.660889,614.573181,1.77499998,0.309570313]    <- should be A
0.240   40/217:                  {0, 1}  0: 2288.70557,477.668274,1.30000007,0.248535156 A   1:(976.660889,614.573181,1.72500002,0.303710938)    <- should be A
0.240   41/217:                  {0, 1}  0: 2349.18433,438.695343,1.31250000,0.248535156     1:[976.660889,614.573181,1.72500002,0.303710938]    <- should be A
0.248   42/217:           MOVE   {0, 1}  0: 2288.70557,477.668274,1.31250000,0.248535156 A   1: 938.674072,624.566284,1.68750000,0.301757813
0.248   43/217:           MOVE   {0, 1}  0: 2365.17871,429.701599,1.32500005,0.247558594     1: 976.660889,614.573181,1.68750000,0.301757813     <- should be A
0.257   44/217:                  {0, 1}  0: 2288.70557,477.668274,1.32500005,0.247558594 A   1: 877.695251,645.551697,1.64999998,0.295898438
0.257   45/217:           MOVE   {0, 1}  0: 2381.17334,421.707153,1.32500005,0.249511719     1: 976.660889,614.573181,1.64999998,0.295898438    <- should be A
0.265   46/217:                  {0, 1}  0: 2288.70557,477.668274,1.32500005,0.249511719 A   1: 829.711914,665.537842,1.61250007,0.293457031
0.265   47/217:           MOVE   {0, 1}  0: 2396.16797,413.712708,1.33749998,0.249511719     1: 976.660889,614.573181,1.61250007,0.293457031    <- should be A
0.274   48/217:                  {0, 1}  0: 2288.70557,477.668274,1.33749998,0.249511719 A   1: 785.727173,689.521179,1.56250000,0.288574219
0.274   49/217:                  {0, 1}  0: 2410.16309,406.717560,1.33749998,0.248046875     1: 976.660889,614.573181,1.56250000,0.288574219    <- should be A
0.282   50/217:           MOVE   {0, 1}  0: 2288.70557,477.668274,1.33749998,0.248046875 A   1: 752.738647,713.504517,1.52499998,0.286132813
0.282   51/217:           MOVE   {0, 1}  0: 2424.15845,400.721710,1.35000002,0.248046875     1: 976.660889,614.573181,1.52499998,0.286132813    <- should be A
0.291   52/217:                  {0, 1}  0: 2288.70557,477.668274,1.35000002,0.248046875 A   1: 733.745239,737.487854,1.48750007,0.281250000
0.291   53/217:           MOVE   {0, 1}  0: 2437.15381,394.725891,1.35000002,0.249023438     1: 976.660889,614.573181,1.48750007,0.281250000    <- should be A
0.299   54/217:                  {0, 1}  0: 2288.70557,477.668274,1.35000002,0.249023438 A   1: 723.748718,761.471191,1.45000005,0.279296875
0.299   55/217:           MOVE   {0, 1}  0: 2449.14966,389.729340,1.36250007,0.249023438     1: 976.660889,614.573181,1.45000005,0.279296875    <- should be A
0.308   56/217:                  {0, 1}  0: 2288.70557,477.668274,1.36250007,0.249023438 A   1: 718.750427,786.453857,1.41250002,0.274902344
0.308   57/217:           MOVE   {0, 1}  0: 2460.14575,384.732819,1.36250007,0.249023438     1: 976.660889,614.573181,1.41250002,0.274902344    <- should be A
0.316   58/217:                  {0, 1}  0: 2288.70557,477.668274,1.36250007,0.249023438 A   1: 709.753601,809.437866,1.35000002,0.270019531
0.316   59/217:           MOVE   {0, 1}  0: 2468.14307,379.736298,1.37500000,0.250000000     1: 976.660889,614.573181,1.35000002,0.270019531    <- should be A
0.323   60/217:           MOVE   {0, 1}  0: 2476.14038,375.739075,1.37500000,0.251953125     1:[976.660889,614.573181,1.35000002,0.270019531]    <- should be A
0.332   61/217:    POINTER_UP(1) {0, 1}  0:[2476.14038,375.739075,1.37500000,0.251953125]    1:[976.660889,614.573181,1.35000002,0.270019531]    <- should be A
0.332   62/217:                  {0}     0: 2484.13745,371.741852,1.38750005,0.250976563
0.339   63/217:           MOVE   {0}     0: 2489.33521,369.143951,1.38750005,0.250976563
*/
//
// https://android-review.googlesource.com/c/platform/frameworks/native/+/640606/
//      1. Make a straightforward wrapper.
//         Pros:
//              - don't need to tweak listeners
//         Cons:
//              - can't construct using historical data; have to unwrap,
//                so apps that use it won't be able to exercise historical data
//      2. Make a tweaked one, using an alternate class FixedMotionEvent.
//         Cons:
//              - requires tweaking listeners-- yeah, pretty much a mess
//              - I'll miss some pieces of the API
//      3. Provide both?
//         That is, provide:
//              FixedOnTouchListener
//                - is an OnTouchListener
//                - onTouch creates a FixedMotionEvent and calls subclass-provided onFixedTouch with it
//              OnTouchListenerBugWorkaroundWrapper
//                - is an OnTouchListener
//                - wraps an OnTouchListener, onTouch delegates to its onTouch
//                      (with historical events turned into main events, since it appears to be impossible to
//                      create a MotionEvent with historical stuff)
//                - implemented as a FixedOnTouchListener
//      OH WAIT!  Does addBatch add historical?  yes!!  so I can do it!
//      ISSUE: HOVER_MOVE?  don't need to handle it because it isn't a touch event
//      ISSUE: ACTION_CANCEL? never seen one, but have to handle it.
//      Q: can I tweak the incoming??
//         i.e. with setX(), setY(), getHistorical and tweak those... ?
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
import java.util.ArrayList;
import java.util.HashMap;

import static com.example.donhatch.multitouchbugworkaround.CHECK.*;
import static com.example.donhatch.multitouchbugworkaround.STRINGIFY.STRINGIFY;


public class FixedOnTouchListener implements View.OnTouchListener {

  private View.OnTouchListener wrapped;

  public FixedOnTouchListener(View.OnTouchListener wrapped) {
    this.wrapped = wrapped;
  }


  public static class OnTouchListenerTestWrapper implements View.OnTouchListener {
    View.OnTouchListener wrapped;
    public OnTouchListenerTestWrapper(View.OnTouchListener wrapped) {
      this.wrapped = wrapped;
    }
    private void simpleDumpMotionEvent(MotionEvent motionEvent) {
      int historySize = motionEvent.getHistorySize();
      int pointerCount = motionEvent.getPointerCount();
      for (int h = 0; h < historySize+1; ++h) {
        if (h==historySize) {
          Log.i(TAG, "          now:");
        } else {
          Log.i(TAG, "          "+h+"/"+historySize+":");
        }
        for (int index = 0; index < pointerCount; ++index) {
          Log.i(TAG, "              id="+motionEvent.getPointerId(index)+": "+(h==historySize?motionEvent.getX():motionEvent.getHistoricalX(index,h))+","+(h==historySize?motionEvent.getY():motionEvent.getHistoricalY(index,h))+"");
        }
      }
    }
    @Override public boolean onTouch(View view, MotionEvent motionEvent) {
      Log.i(TAG, "    in OnTouchListenerTestWrapper.onTouch");
      Log.i(TAG, "      historySize = "+motionEvent.getHistorySize());

      if (false) {
        Log.i(TAG, "      before tweaking by -XXX,-XXX:");
        simpleDumpMotionEvent(motionEvent);

        if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN || motionEvent.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
          // Can I just tweak the motion event in place?
          // Let's try messing with its X and Y.
          motionEvent.setLocation(motionEvent.getX()-5000,
                                  motionEvent.getY()-5000);
          // Result: it seems to tweak *all* of the event! even the historical stuff!  wow!  and for all ids!? holy moly!
          // hmm, I guess that's what its doc says: "Applies offsetLocation(float, float) with a delta from the current location to the given new location."
        }

        Log.i(TAG, "      after tweaking by -XXX,-XXX:");
        simpleDumpMotionEvent(motionEvent);
      } 

      if (true) {
        Log.i(TAG, "      before tweaking a historical:");
        simpleDumpMotionEvent(motionEvent);

        //MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords);
        //getPointerCoords(pointerIndex, pointerCoords);
        // Bleah, I don't see how to tweak the historical.  I guess I'll create one from scratch then.

        Log.i(TAG, "      after tweaking a historical:");
        simpleDumpMotionEvent(motionEvent);
      }



      Log.i(TAG, "      calling wrapped.onTouch");
      boolean answer = wrapped.onTouch(view, motionEvent);
      Log.i(TAG, "      returned from wrapped.onTouch: "+answer);
      Log.i(TAG, "    out OnTouchListenerTestWrapper.onTouch, returning "+answer);
      return answer;
    }
  }

  private static final String TAG = MultiTouchBugWorkaroundActivity.class.getSimpleName();  // XXX

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
    public float[/*max_id_occurring+1*/] x;
    public float[/*max_id_occurring+1*/] y;
    public float[/*max_id_occurring+1*/] pressure;
    public float[/*max_id_occurring+1*/] size;
    // orientation seems to be useless for this analysis; it's always +-1.57079637
    public float[/*max_id_occurring+1*/] orientation;
    // dammit! relative_x,relative_y always zero.
    public float[/*ditto*/] relative_x;
    public float[/*ditto*/] relative_y;
    // oh screw this, get them all!
    public MotionEvent.PointerCoords[] all_axis_values;
    // rawX()/rawY() doesn't seem to be helpful: (1) can't query it per-history nor per-pointer, (2) the bug apparently affects this too so it doesn't give any clues

    // all arrays assumed to be immutable.
    public LogicalMotionEvent(boolean isHistorical, long eventTimeMillis, int action, int actionId, int ids[], float[] x, float[] y, float[] pressure, float[] size, float[] orientation, float[] relative_x, float[] relative_y, MotionEvent.PointerCoords[] all_axis_values) {
      this.isHistorical = isHistorical;
      this.eventTimeMillis = eventTimeMillis;
      this.action = action;
      this.actionId = actionId;
      this.ids = ids;
      this.x = x;
      this.y = y;
      this.pressure = pressure;
      this.size = size;
      this.orientation = orientation;
      this.relative_x = relative_x;
      this.relative_y = relative_y;
      this.all_axis_values = all_axis_values;
    }
    // Breaks motionEvent down into LogicalMotionEvents and appends them to list.
    public static void breakDown(MotionEvent motionEvent, ArrayList<LogicalMotionEvent> list) {
      final int action = motionEvent.getActionMasked();
      final int actionId = motionEvent.getPointerId(motionEvent.getActionIndex());
      final int pointerCount = motionEvent.getPointerCount();
      final int historySize = motionEvent.getHistorySize();
      final int[] ids = new int[pointerCount];
      int maxIdOccurring = -1;
      for (int i = 0; i < pointerCount; ++i) {
        ids[i] = motionEvent.getPointerId(i);
        maxIdOccurring = Math.max(maxIdOccurring, ids[i]);
      }
      for (int h = 0; h < historySize+1; ++h) {
        final long eventTimeMillis = h==historySize ? motionEvent.getEventTime() : motionEvent.getHistoricalEventTime(h);
        final float[] x = new float[maxIdOccurring+1];
        final float[] y = new float[maxIdOccurring+1];
        final float[] pressure = new float[maxIdOccurring+1];
        final float[] size = new float[maxIdOccurring+1];
        final float[] orientation = new float[maxIdOccurring+1];
        final float[] relative_x = new float[maxIdOccurring+1];
        final float[] relative_y = new float[maxIdOccurring+1];
        final MotionEvent.PointerCoords[] all_axis_values = new MotionEvent.PointerCoords[maxIdOccurring+1];
        for (int id = 0; id < maxIdOccurring+1; ++id) {
          x[id] = Float.NaN;
          y[id] = Float.NaN;
          pressure[id] = Float.NaN;
          size[id] = Float.NaN;
          orientation[id] = Float.NaN;
          relative_x[id] = Float.NaN;
          relative_y[id] = Float.NaN;
          all_axis_values[id] = new MotionEvent.PointerCoords();
        }
        for (int index = 0; index < pointerCount; ++index) {
          final int id = ids[index]; motionEvent.getPointerId(index);
          x[id] = h==historySize ? motionEvent.getX(index) : motionEvent.getHistoricalX(index, h);
          y[id] = h==historySize ? motionEvent.getY(index) : motionEvent.getHistoricalY(index, h);
          pressure[id] = h==historySize ? motionEvent.getPressure(index) : motionEvent.getHistoricalPressure(index, h);
          size[id] = h==historySize ? motionEvent.getSize(index) : motionEvent.getHistoricalSize(index, h);
          orientation[id] = h==historySize ? motionEvent.getOrientation(index) : motionEvent.getHistoricalOrientation(index, h);
          CHECK_EQ(Math.abs(orientation[id]), 1.57079637f);  // XXX not sure if this is reliable on all devices, but it's what I always get
          relative_x[id] = h==historySize ? motionEvent.getAxisValue(MotionEvent.AXIS_RELATIVE_X,index) : motionEvent.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, index, h);
          relative_y[id] = h==historySize ? motionEvent.getAxisValue(MotionEvent.AXIS_RELATIVE_Y,index) : motionEvent.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, index, h);
          CHECK_EQ(Math.abs(relative_x[id]), 0.f);  // XXX not sure if this is reliable on all devices, but it's what I always get
          CHECK_EQ(Math.abs(relative_y[id]), 0.f);  // XXX not sure if this is reliable on all devices, but it's what I always get
          if (h==historySize) {
            motionEvent.getPointerCoords(index, all_axis_values[id]);
          } else {
            motionEvent.getHistoricalPointerCoords(index, h, all_axis_values[id]);
          }
        }
        list.add(new LogicalMotionEvent(h<historySize, eventTimeMillis, action, actionId, ids, x, y, pressure, size, orientation, relative_x, relative_y, all_axis_values));
      }
    }

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

    public static void dump(ArrayList<LogicalMotionEvent> list) {
      int n = list.size();

      int idsWidth = 0;
      for (int i = 0; i < n; ++i) {
        idsWidth = Math.max(idsWidth, STRINGIFY(list.get(i).ids).length());
      }

      int max_id_occurring = 9; // XXX fudge

      // For each id,
      // whenever there are 2 or more distinct runs with the same coords
      // within a given down-period of that id,
      // make a label for those coords.
      final String[][] id2index2label = new String[max_id_occurring+1][n];  // nulls initially
      {
        for (int id = 0; id <= max_id_occurring; ++id) {
          String[] index2label = new String[n];  // nulls initially

          String[] index2fingerprint = new String[n];  // nulls initially
          for (int i = 0; i < n; ++i) {
            LogicalMotionEvent e = list.get(i);
            if (id < e.x.length && !Float.isNaN(e.x[id])) {
              String fingerprint = e.x[id]+","+e.y[id];
              index2fingerprint[i] = fingerprint;
            }
          }

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
                boolean isRunStart = (i==0 || index2fingerprint[i-1]==null || !fingerprint.equals(index2fingerprint[i-1]));
                if (isRunStart) {
                  fingerprint2nRunsHolder.get(fingerprint)[0]++;
                }
              }
            }
            HashMap<String,String> fingerprint2label = new HashMap<String,String>();
            int nLabels = 0;
            for (int i = 0; i < n; ++i) {
              String fingerprint = index2fingerprint[i];
              if (fingerprint != null) {
                boolean isRunStart = (i==0 || index2fingerprint[i-1]!=null || !fingerprint.equals(index2fingerprint[i-1]));
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


          id2index2label[id] = index2label;
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
          xOfInterestForId0 = e.x[0];
          yOfInterestForId0 = e.y[0];
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
            CHECK_LT(idOfInterest, e.x.length);
            xOfInterestForIdNonzero = e.x[idOfInterest];
            yOfInterestForIdNonzero = e.y[idOfInterest];
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
                                STRINGIFY(e.ids)));
        for (int id = 0; id < e.x.length; ++id) {
          if (Float.isNaN(e.x[id])) {
            //sb.append(String.format("%29s", ""));
            sb.append(String.format("%52s", "", ""));
          } else {
            //String coordsString = String.format("%.9g,%.9g", e.x[id], e.y[id]);
            String coordsString = String.format("%.9g,%.9g,%.9g,%.9g", e.x[id], e.y[id], e.pressure[id], e.size[id]);

            boolean parenthesized = false;
            if (i >= 1) {
              LogicalMotionEvent ePrev = list.get(i-1);
              if (id < ePrev.x.length && e.x[id] == ePrev.x[id]
                                      && e.y[id] == ePrev.y[id]) {
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

                if (id < ePrev.x.length
                 && e.pressure[id] == ePrev.pressure[id]
                 && e.size[id] == ePrev.size[id]
                 //&& (e.x[id] != ePrev.x[id] || e.y[id] != ePrev.y[id])   // XXX wrong!  need to be comparing with previous *corrected*
                 && (e.x[id] != mostRecentNonSuspiciousX[id] || e.y[id] != mostRecentNonSuspiciousY[id])   // XXX wrong!  need to be comparing with previous *corrected*
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
              if (id == 0 && (e.x[0] != xOfInterestForId0 || e.y[0] != yOfInterestForId0)) {  // id 0 only bugs on the coords on which it went down
                //Log.i(TAG, "    KILLING QUESTION MARK on "+id+" BECAUSE the 0 suepect and not the coords on which it went down");
                foundSmokingGun = false;
              }
              if (id != 0 && (e.x[id] != xOfInterestForIdNonzero || e.y[id] != yOfInterestForIdNonzero)) {  // id nonzero only bugs on those coords. when not decided yet, it doesn't bug
                //Log.i(TAG, "    KILLING QUESTION MARK on "+id+" BECAUSE the nonzero suspect and not the coords two after the POINTER_DOWN(0) event which are "+xOfInterestForIdNonzero+","+yOfInterestForIdNonzero+"");
                foundSmokingGun = false;
              }

              if (!foundSmokingGun) {
                mostRecentNonSuspiciousX[id] = e.x[id];
                mostRecentNonSuspiciousY[id] = e.y[id];
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
        Log.i(TAG, sb.toString());
      }
    }  // dump

    // Read back in a string produced by dump().
    public static ArrayList<LogicalMotionEvent> parseDump(String dump) {
      final ArrayList<LogicalMotionEvent> answer = new ArrayList<LogicalMotionEvent>();
      final String[] lines = dump.split("\n");
      for (final String line : lines) {
      //...
        //answer.add(new LogicalMotionEvent(h<historySize, eventTimeMillis, action, actionId, ids, x, y, pressure, size, orientation, relative_x, relative_y, all_axis_values));
      }
      return null; // XXX
    }  // parseDump
/*
// How did this happen?  Let's try reading it back in
0.189   28/217:                  {0}     0: 2241.22192,512.643982,1.27499998,0.246582031
0.194   29/217:           MOVE   {0}     0: 2250.21875,505.648834,1.27499998,0.246582031
0.198   30/217:           MOVE   {0}     0: 2260.21533,498.653717,1.27499998,0.246582031
0.206   31/217:  POINTER_DOWN(1) {0, 1}  0:[2260.21533,498.653717,1.27499998,0.246582031]    1: 976.660889,614.573181,1.87500000,0.322265625    <- should be A
0.206   32/217:                  {0, 1}  0: 2279.20874,484.663422,1.28750002,0.246582031     1:[976.660889,614.573181,1.87500000,0.322265625]    <- should be A
0.210   33/217:           MOVE   {0, 1}  0: 2288.70557,477.668274,1.28750002,0.246582031 A   1:[976.660889,614.573181,1.87500000,0.322265625]    <- should be A
0.214   34/217:                  {0, 1}  0:[2288.70557,477.668274,1.28750002,0.246582031]A   1:(976.660889,614.573181,1.85000002,0.317382813)    <- should be A
0.214   35/217:           MOVE   {0, 1}  0: 2297.20239,471.672455,1.28750002,0.247558594     1:[976.660889,614.573181,1.85000002,0.317382813]    <- should be A
0.223   36/217:                  {0, 1}  0: 2288.70557,477.668274,1.28750002,0.247558594 A   1:(976.660889,614.573181,1.81250000,0.311523438)    <- should be A
0.223   37/217:           MOVE   {0, 1}  0: 2315.19629,459.680756,1.30000007,0.247558594     1:[976.660889,614.573181,1.81250000,0.311523438]    <- should be A
0.231   38/217:                  {0, 1}  0: 2288.70557,477.668274,1.30000007,0.247558594 A   1:(976.660889,614.573181,1.77499998,0.309570313)    <- should be A
0.231   39/217:           MOVE   {0, 1}  0: 2332.19019,448.688416,1.30000007,0.248535156     1:[976.660889,614.573181,1.77499998,0.309570313]    <- should be A
0.240   40/217:                  {0, 1}  0: 2288.70557,477.668274,1.30000007,0.248535156 A   1:(976.660889,614.573181,1.72500002,0.303710938)    <- should be A
0.240   41/217:                  {0, 1}  0: 2349.18433,438.695343,1.31250000,0.248535156     1:[976.660889,614.573181,1.72500002,0.303710938]    <- should be A
0.248   42/217:           MOVE   {0, 1}  0: 2288.70557,477.668274,1.31250000,0.248535156 A   1: 938.674072,624.566284,1.68750000,0.301757813
0.248   43/217:           MOVE   {0, 1}  0: 2365.17871,429.701599,1.32500005,0.247558594     1: 976.660889,614.573181,1.68750000,0.301757813     <- should be A
0.257   44/217:                  {0, 1}  0: 2288.70557,477.668274,1.32500005,0.247558594 A   1: 877.695251,645.551697,1.64999998,0.295898438
0.257   45/217:           MOVE   {0, 1}  0: 2381.17334,421.707153,1.32500005,0.249511719     1: 976.660889,614.573181,1.64999998,0.295898438    <- should be A
0.265   46/217:                  {0, 1}  0: 2288.70557,477.668274,1.32500005,0.249511719 A   1: 829.711914,665.537842,1.61250007,0.293457031
0.265   47/217:           MOVE   {0, 1}  0: 2396.16797,413.712708,1.33749998,0.249511719     1: 976.660889,614.573181,1.61250007,0.293457031    <- should be A
0.274   48/217:                  {0, 1}  0: 2288.70557,477.668274,1.33749998,0.249511719 A   1: 785.727173,689.521179,1.56250000,0.288574219
0.274   49/217:                  {0, 1}  0: 2410.16309,406.717560,1.33749998,0.248046875     1: 976.660889,614.573181,1.56250000,0.288574219    <- should be A
0.282   50/217:           MOVE   {0, 1}  0: 2288.70557,477.668274,1.33749998,0.248046875 A   1: 752.738647,713.504517,1.52499998,0.286132813
0.282   51/217:           MOVE   {0, 1}  0: 2424.15845,400.721710,1.35000002,0.248046875     1: 976.660889,614.573181,1.52499998,0.286132813    <- should be A
0.291   52/217:                  {0, 1}  0: 2288.70557,477.668274,1.35000002,0.248046875 A   1: 733.745239,737.487854,1.48750007,0.281250000
0.291   53/217:           MOVE   {0, 1}  0: 2437.15381,394.725891,1.35000002,0.249023438     1: 976.660889,614.573181,1.48750007,0.281250000    <- should be A
0.299   54/217:                  {0, 1}  0: 2288.70557,477.668274,1.35000002,0.249023438 A   1: 723.748718,761.471191,1.45000005,0.279296875
0.299   55/217:           MOVE   {0, 1}  0: 2449.14966,389.729340,1.36250007,0.249023438     1: 976.660889,614.573181,1.45000005,0.279296875    <- should be A
0.308   56/217:                  {0, 1}  0: 2288.70557,477.668274,1.36250007,0.249023438 A   1: 718.750427,786.453857,1.41250002,0.274902344
0.308   57/217:           MOVE   {0, 1}  0: 2460.14575,384.732819,1.36250007,0.249023438     1: 976.660889,614.573181,1.41250002,0.274902344    <- should be A
0.316   58/217:                  {0, 1}  0: 2288.70557,477.668274,1.36250007,0.249023438 A   1: 709.753601,809.437866,1.35000002,0.270019531
0.316   59/217:           MOVE   {0, 1}  0: 2468.14307,379.736298,1.37500000,0.250000000     1: 976.660889,614.573181,1.35000002,0.270019531    <- should be A
0.323   60/217:           MOVE   {0, 1}  0: 2476.14038,375.739075,1.37500000,0.251953125     1:[976.660889,614.573181,1.35000002,0.270019531]    <- should be A
0.332   61/217:    POINTER_UP(1) {0, 1}  0:[2476.14038,375.739075,1.37500000,0.251953125]    1:[976.660889,614.573181,1.35000002,0.270019531]    <- should be A
0.332   62/217:                  {0}     0: 2484.13745,371.741852,1.38750005,0.250976563
0.339   63/217:           MOVE   {0}     0: 2489.33521,369.143951,1.38750005,0.250976563
*/
  }  // class LogicalMotionEvent

  private ArrayList<LogicalMotionEvent> logicalMotionEventsSinceFirstDown = new ArrayList<LogicalMotionEvent>();

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
  private int mId1 = -1;  // never 0.  gets set to >0 when arming (first event seen)
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
  private void moveToSTATE_ARMING(int id1, float anchor0x, float anchor0y) {
    mCurrentState = STATE_ARMING;
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

  private void correctPointerCoordsUsingState(MotionEvent unfixed, int historyIndex, long eventTime, MotionEvent.PointerCoords pointerCoords[]) {
    final int verboseLevel = 1;
    if (verboseLevel >= 1) Log.i(TAG, "        in correctPointerCoordsUsingState(historyIndex="+historyIndex+"/"+unfixed.getHistorySize()+", eventTime="+(eventTime-unfixed.getDownTime())/1000.+")  before: "+stateToString(mCurrentState));
    if (verboseLevel >= 1) Log.i(TAG, "          before: id1="+mId1+" anchor0="+mAnchor0x+","+mAnchor0y+" anchor1="+mAnchor1x+","+mAnchor1y+" lkg0="+mLastKnownGood0x+","+mLastKnownGood0y+" lkg1="+mLastKnownGood1x+","+mLastKnownGood1y+"");
    final int action = unfixed.getActionMasked();
    final int pointerCount = unfixed.getPointerCount();
    final int historySize = unfixed.getHistorySize();

    // No matter what state we're in,
    // if we see the first event of the arming sequence,
    // honor it.
    if (action == MotionEvent.ACTION_POINTER_DOWN
     && pointerCount == 2
     && unfixed.getActionIndex() == 0
     && unfixed.getPointerId(0) == 0) {
      moveToSTATE_ARMING(/*id1=*/unfixed.getPointerId(1),
                         /*anchor0x=*/pointerCoords[0].x,
                         /*anchor0y=*/pointerCoords[0].y);
    } else if (mCurrentState == STATE_ARMING) {
      if (action == MotionEvent.ACTION_MOVE) {
        if (historyIndex == historySize  // i.e. this is the "primary" sub-event, not historical
         && pointerCount == 2
         && unfixed.getPointerId(0) == 0
         && unfixed.getPointerId(1) == mId1) {
          moveToSTATE_ARMED(/*anchor1x=*/pointerCoords[1].x,
                            /*anchor1y=*/pointerCoords[1].y,
                            /*lastKnownGood0x=*/pointerCoords[0].x,
                            /*lastKnownGood0y=*/pointerCoords[0].y,
                            /*lastKnownGood1x=*/pointerCoords[1].x,
                            /*lastKnownGood1y=*/pointerCoords[1].y);
        } else {
          // We're in a historical sub-event of what may be the arming event.  Do nothing special.
        }
      } else {
        // Didn't see the second event of the arming sequence; disarm.
        moveToSTATE_DISARMED();
      }
    } else if (mCurrentState == STATE_ARMED) {  // i.e. if was already armed (not just now got armed)
      if (unfixed.getPointerId(0) == 0) {
        // id 0 is still down (although id mId1 might not be).
        if (pointerCoords[0].x == mAnchor0x
         && pointerCoords[0].y == mAnchor0y) {
          // Pointer 0 moved to (or stayed still at) the anchor.
          // That's what happens when it meant to stay at mLastKnownGood0.
          // Correct it.
          pointerCoords[0].x = mLastKnownGood0x;
          pointerCoords[0].y = mLastKnownGood0y;
        } else {
          if (pointerCoords[0].x == mLastKnownGood0x
           && pointerCoords[0].y == mLastKnownGood0y) {
            // Pointer 0 stayed the same, and is *not* the anchor.
            // The bug is not happening (since, when the bug is happening,
            // staying the same always gets botched into moving to the anchor).
            if (verboseLevel >= 1) Log.i(TAG, "          pointer 0 stayed the same at "+mLastKnownGood0x+","+mLastKnownGood0y+", and is *not* anchor. bug isn't happening (or isn't happening any more).");
            moveToSTATE_DISARMED();
          } else {
            mLastKnownGood0x = pointerCoords[0].x;
            mLastKnownGood0y = pointerCoords[0].y;
          }
        }
      }
      if (mCurrentState == STATE_ARMED) {  // still, i.e. if we didn't just disarm
        CHECK_GE(mId1, 1);
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
    /*


         out correctPointerCoordsUsingState(historyIndex=1/1, eventTime=0.599)  after: DISARMED

         in correctPointerCoordsUsingState(historyIndex=0/0, eventTime=0.602)  before: DISARMED
           before: id1=-1 anchor0=NaN,NaN anchor1=NaN,NaN lkg0=NaN,NaN lkg1=NaN,NaN
           after: id1=1 anchor0=1294.5505,931.3532 anchor1=NaN,NaN lkg0=NaN,NaN lkg1=NaN,NaN
         out correctPointerCoordsUsingState(historyIndex=0/0, eventTime=0.602)  after: ARMING
         in correctPointerCoordsUsingState(historyIndex=0/0, eventTime=0.602)  before: ARMING
           before: id1=1 anchor0=1294.5505,931.3532 anchor1=NaN,NaN lkg0=NaN,NaN lkg1=NaN,NaN
           after: id1=1 anchor0=1294.5505,931.3532 anchor1=NaN,NaN lkg0=NaN,NaN lkg1=NaN,NaN
         out correctPointerCoordsUsingState(historyIndex=0/0, eventTime=0.602)  after: ARMING
         in correctPointerCoordsUsingState(historyIndex=0/1, eventTime=0.602)  before: ARMING
           before: id1=1 anchor0=1294.5505,931.3532 anchor1=NaN,NaN lkg0=NaN,NaN lkg1=NaN,NaN
           after: id1=1 anchor0=1294.5505,931.3532 anchor1=NaN,NaN lkg0=NaN,NaN lkg1=NaN,NaN
         out correctPointerCoordsUsingState(historyIndex=0/1, eventTime=0.602)  after: ARMING

         in correctPointerCoordsUsingState(historyIndex=1/1, eventTime=0.606)  before: ARMING
           before: id1=1 anchor0=1294.5505,931.3532 anchor1=NaN,NaN lkg0=NaN,NaN lkg1=NaN,NaN
           after: id1=1 anchor0=1294.5505,931.3532 anchor1=2014.8005,821.42957 lkg0=1294.5505,931.3532 lkg1=2014.8005,821.42957
         out correctPointerCoordsUsingState(historyIndex=1/1, eventTime=0.606)  after: ARMED
         in correctPointerCoordsUsingState(historyIndex=1/1, eventTime=0.606)  before: ARMED
           before: id1=1 anchor0=1294.5505,931.3532 anchor1=2014.8005,821.42957 lkg0=1294.5505,931.3532 lkg1=2014.8005,821.42957
           after: id1=1 anchor0=1294.5505,931.3532 anchor1=2014.8005,821.42957 lkg0=1294.5505,931.3532 lkg1=2014.8005,821.42957
         out correctPointerCoordsUsingState(historyIndex=1/1, eventTime=0.606)  after: ARMED
         in correctPointerCoordsUsingState(historyIndex=0/1, eventTime=0.611)  before: ARMED
           before: id1=1 anchor0=1294.5505,931.3532 anchor1=2014.8005,821.42957 lkg0=1294.5505,931.3532 lkg1=2014.8005,821.42957
           after: id1=1 anchor0=1294.5505,931.3532 anchor1=2014.8005,821.42957 lkg0=1294.5505,931.3532 lkg1=2014.8005,821.42957
         out correctPointerCoordsUsingState(historyIndex=0/1, eventTime=0.611)  after: ARMED
         in correctPointerCoordsUsingState(historyIndex=1/1, eventTime=0.611)  before: ARMED
           before: id1=1 anchor0=1294.5505,931.3532 anchor1=2014.8005,821.42957 lkg0=1294.5505,931.3532 lkg1=2014.8005,821.42957
           after: id1=1 anchor0=1294.5505,931.3532 anchor1=2014.8005,821.42957 lkg0=1294.5505,931.3532 lkg1=2018.2992,819.43097
         out correctPointerCoordsUsingState(historyIndex=1/1, eventTime=0.611)  after: ARMED
         in correctPointerCoordsUsingState(historyIndex=1/1, eventTime=0.611)  before: ARMED
           before: id1=1 anchor0=1294.5505,931.3532 anchor1=2014.8005,821.42957 lkg0=1294.5505,931.3532 lkg1=2018.2992,819.43097
           pointer mId1=1 stayed the same at 2018.2992,819.43097, and is *not* anchor. bug isn't happening.
           after: id1=-1 anchor0=NaN,NaN anchor1=NaN,NaN lkg0=NaN,NaN lkg1=NaN,NaN
         out correctPointerCoordsUsingState(historyIndex=1/1, eventTime=0.611)  after: DISARMED
0.595  123/199:                  {1}                                                         1: 2010.30200,830.423279,1.21249998,0.232910156 A
0.599  124/199:           MOVE   {1}                                                         1: 2012.30127,827.425354,1.21249998,0.232910156
0.602  125/199:  POINTER_DOWN(0) {0, 1}  0: 1294.55054,931.353210,1.72500002,0.306152344 A!  1: 2010.30200,830.423279,1.21249998,0.232910156 A
0.602  126/199:                  {0, 1}  0:[1294.55054,931.353210,1.72500002,0.306152344]A   1: 2013.30103,824.427490,1.21249998,0.232910156
0.606  127/199:           MOVE   {0, 1}  0:[1294.55054,931.353210,1.72500002,0.306152344]A   1: 2014.80054,821.429565,1.21249998,0.232910156 B!
0.611  128/199:                  {0, 1}  0:(1294.55054,931.353210,1.72500002,0.305175781)A   1:[2014.80054,821.429565,1.21249998,0.232910156]B?
0.611  129/199:           MOVE   {0, 1}  0:[1294.55054,931.353210,1.72500002,0.305175781]A   1: 2018.29919,819.430969,1.21249998,0.232910156
0.619  130/199:                  {0, 1}  0:(1294.55054,931.353210,1.72500002,0.304687500)A   1: 2014.80054,821.429565,1.21249998,0.232910156 B?
0.619  131/199:           MOVE   {0, 1}  0:[1294.55054,931.353210,1.72500002,0.304687500]A   1: 2023.29749,812.435791,1.21249998,0.233886719
0.628  132/199:                  {0, 1}  0:(1294.55054,931.353210,1.71249998,0.302734375)A   1: 2014.80054,821.429565,1.21249998,0.233886719 B?
    */

    if (verboseLevel >= 1) Log.i(TAG, "          after: id1="+mId1+" anchor0="+mAnchor0x+","+mAnchor0y+" anchor1="+mAnchor1x+","+mAnchor1y+" lkg0="+mLastKnownGood0x+","+mLastKnownGood0y+" lkg1="+mLastKnownGood1x+","+mLastKnownGood1y+"");
    if (verboseLevel >= 1) Log.i(TAG, "        out correctPointerCoordsUsingState(historyIndex="+historyIndex+"/"+unfixed.getHistorySize()+", eventTime="+(eventTime-unfixed.getDownTime())/1000.+")  after: "+stateToString(mCurrentState));
  }  // correctPointerCoordsUsingState

  // Framework calls this; we create a fixed MotionEvent
  // and call the wrapped listener on it.
  @Override
  final public boolean onTouch(View view, MotionEvent unfixed) {
    final int verboseLevel = 1;  // 0: nothing, 1: dump entire sequence on final UP, 2: and in/out
    if (verboseLevel >= 2) Log.i(TAG, "    in FixedOnTouchListener onTouch");

    LogicalMotionEvent.breakDown(unfixed, logicalMotionEventsSinceFirstDown);  // for post-mortem analysis

    boolean answer;
    {
      final int pointerCount = unfixed.getPointerCount();
      final int historySize = unfixed.getHistorySize();

      final MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[pointerCount];  // CBB: reuse
      for (int index = 0; index < pointerCount; ++index) {
        pointerProperties[index] = new MotionEvent.PointerProperties();
        unfixed.getPointerProperties(index, pointerProperties[index]);
      }

      // Need to correct the pointer coords in history order,
      // before creating the fixed motion event,
      // since the args to obtaining the motion event include the last (i.e. non-historical) sub-event of the history.
      final MotionEvent.PointerCoords[][] pointerCoords = new MotionEvent.PointerCoords[historySize+1][pointerCount];  // CBB: reuse
      for (int h = 0; h < historySize+1; ++h) {
        for (int index = 0; index < pointerCount; ++index) {
          pointerCoords[h][index] = new MotionEvent.PointerCoords();
          if (h==historySize) {
            unfixed.getPointerCoords(index, pointerCoords[h][index]);
          } else {
            unfixed.getHistoricalPointerCoords(index, h, pointerCoords[h][index]);
          }
        }
        long subEventTime = h==historySize ? unfixed.getEventTime() : unfixed.getHistoricalEventTime(h);
        correctPointerCoordsUsingState(unfixed, h, subEventTime, pointerCoords[h]);
      }
      MotionEvent fixed = MotionEvent.obtain(
          unfixed.getDownTime(),
          unfixed.getEventTime(),
          unfixed.getAction(),  // not getActionMasked(), apparently
          pointerCount,
          pointerProperties,
          pointerCoords[historySize],
          unfixed.getMetaState(),
          unfixed.getButtonState(),
          unfixed.getXPrecision(),
          unfixed.getYPrecision(),
          unfixed.getDeviceId(),
          unfixed.getEdgeFlags(),
          unfixed.getSource(),
          unfixed.getFlags());
      try {
        CHECK_EQ(fixed.getAction(), unfixed.getAction());
        CHECK_EQ(fixed.getActionMasked(), unfixed.getActionMasked());
        CHECK_EQ(fixed.getActionIndex(), unfixed.getActionIndex());
        for (int h = 0; h < historySize; ++h) {
          int historicalMetaState = unfixed.getMetaState(); // huh? this can't be right, but I don't see any way to query metaState per-history
          fixed.addBatch(unfixed.getHistoricalEventTime(h),
                         pointerCoords[h],
                         historicalMetaState);
        }
        answer = wrapped.onTouch(view, fixed);
      } finally {
        fixed.recycle();
      }
    }

    if (unfixed.getActionMasked() == MotionEvent.ACTION_UP) {
      if (verboseLevel >= 1) {
        Log.i(TAG, "      ===============================================================");
        Log.i(TAG, "      LOGICAL MOTION EVENT SEQUENCE:");
        LogicalMotionEvent.dump(logicalMotionEventsSinceFirstDown);
        Log.i(TAG, "      ===============================================================");
      }
      logicalMotionEventsSinceFirstDown.clear();
    }

    if (verboseLevel >= 2) Log.i(TAG, "    out FixedOnTouchListener onTouch, returning "+answer);
    return answer;
  }  // onTouch
}

