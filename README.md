# 代办
## 1、代码识别以来那块补充完整
## 2、survey补充 
deadlocks accounting for 20\%, memory leaks for 30\%, object initialization anomalies for 30\%


docker pull 4ndychin/llvm-ufo
docker run -it 4ndychin/llvm-ufo
docker start -i  a7c6c8931ba4
docker cp /home/nsas2020/fuzz/targetProcess/reorder a7c6c8931ba4:/ufo
/ufo/build/bin/clang -fsanitize=thread -g -o0 -Wall 0-datarace.cpp -o datarace
UFO_ON=1 UFO_CALL=1 UFO_TDIR=./ufo_test_trace UFO_TL_BUF=512 ./datarace
docker cp a7c6c8931ba4:/ufo/reorder/ufo_test_trace_20  .
/Users/xinguohua/Code/reorder  ufo-predict
trace_dir=/Users/xinguohua/Code/UFO/ufo-predict/ufo_test_trace_20