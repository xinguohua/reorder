#!/bin/bash

# 检查是否提供了源文件参数
if [ -z "$1" ]; then
  echo "请提供源文件作为参数。"
  echo "用法: $0 <源文件>"
  exit 1
fi

# 变量
SOURCE_FILE="$1"
BINARY=$(basename "$SOURCE_FILE" | cut -d. -f1)
DOCKER_CONTAINER_ID="a7c6c8931ba4"
LOCAL_LOG_DIR="/home/nsas2020/fuzz/reorder/log"
LOCAL_MODULE_DIR="/home/nsas2020/fuzz/reorder/module"
UFO_TEST_TRACE_DIR="/ufo/reorder/ufo_test_trace"
UFO_TL_BUF_SIZE=512
CONFIGFILE="/home/nsas2020/fuzz/reorder/config.properties"
# 打印并执行命令，若执行失败则退出
execute_command() {
  echo "执行命令: $1"
  eval $1
  if [ $? -ne 0 ]; then
    echo "命令执行失败: $1"
    exit 1
  fi
}

# 使用ThreadSanitizer编译源文件
#compile_command="docker exec $DOCKER_CONTAINER_ID sh -c \"/ufo/build/bin/clang -fsanitize=thread -g -o0 -Wall /ufo/reorder/$SOURCE_FILE -o /ufo/reorder/$BINARY\""
compile_command="docker exec $DOCKER_CONTAINER_ID sh -c \"/ufo/build/bin/clang++ -std=c++11 -fsanitize=thread -g -o0 -Wall /ufo/reorder/$SOURCE_FILE -o /ufo/reorder/$BINARY\""
execute_command "$compile_command"

# 删除以ufo_test_trace_开头的文件夹
clean_command="docker exec $DOCKER_CONTAINER_ID sh -c 'rm -rf /ufo/reorder/ufo_test_trace_*'"
execute_command "$clean_command"

# shellcheck disable=SC2034
sleep_command="sleep 5"
execute_command "$sleep_command"

# 设置UFO环境变量并运行二进制文件
#docker exec $DOCKER_CONTAINER_ID sh -c "UFO_ON=1 UFO_CALL=1 UFO_TDIR=$UFO_TEST_TRACE_DIR UFO_TL_BUF=$UFO_TL_BUF_SIZE /ufo/reorder/$BINARY"
docker exec $DOCKER_CONTAINER_ID sh -c "timeout 300 sh -c 'UFO_ON=1 UFO_CALL=1 UFO_TDIR=$UFO_TEST_TRACE_DIR UFO_TL_BUF=$UFO_TL_BUF_SIZE /ufo/reorder/$BINARY'"

# 循环直到UFO_TEST_TRACE_PATH不为空或超过20秒
UFO_TEST_TRACE_PATH=""
MAX_ATTEMPTS=20
attempt=0
while [ -z "$UFO_TEST_TRACE_PATH" ] && [ $attempt -lt $MAX_ATTEMPTS ]; do
  UFO_TEST_TRACE_PATH=$(docker exec $DOCKER_CONTAINER_ID sh -c "ls /ufo/reorder | grep '^ufo_test_trace_' | head -n 1")
  if [ -z "$UFO_TEST_TRACE_PATH" ]; then
    sleep 1  # 等待1秒钟再试
    attempt=$((attempt + 1))
  fi
done

if [ -z "$UFO_TEST_TRACE_PATH" ]; then
  echo "在20秒内未找到UFO测试跟踪路径"
  exit 1
else
  echo "获取的UFO测试跟踪路径: $UFO_TEST_TRACE_PATH"
fi


clean_log_command="rm -rf $LOCAL_LOG_DIR/$BINARY"
execute_command "$clean_log_command"

# 从Docker容器中复制目录到本地机器
copy_log_command="docker cp $DOCKER_CONTAINER_ID:/ufo/reorder/$UFO_TEST_TRACE_PATH $LOCAL_LOG_DIR/$BINARY"
execute_command "$copy_log_command"


clean_binary_command="rm -rf $LOCAL_MODULE_DIR/$BINARY"
execute_command "$clean_binary_command"

copy_binary_command="docker cp $DOCKER_CONTAINER_ID:/ufo/reorder/$BINARY $LOCAL_MODULE_DIR"
execute_command "$copy_binary_command"

sed_command="sed -i 's|^trace_dir=.*|trace_dir=$LOCAL_LOG_DIR/$BINARY|' $CONFIGFILE"
execute_command "$sed_command"