#!/bin/bash

function usage() {
  echo "Usage: $0 <jar>"
}

# Print usage if incorrect number of args
[[ $# -ne 1 ]] && usage

MAIN_JAR=$1
SERVER_PORT=$2
SERVER_CLASS_NAME="com.mozilla.bagheera.redis.RedisListPoller"
HADOOP_CONF_PATH=/etc/hadoop/conf
HBASE_CONF_PATH=/etc/hbase/conf

CLASSPATH=$MAIN_JAR:$HADOOP_CONF_PATH:$HBASE_CONF_PATH

for lib in `ls lib/*.jar`;
do
    CLASSPATH=$CLASSPATH:$lib
done

echo $CLASSPATH

java -cp $CLASSPATH $SERVER_CLASS_NAME
