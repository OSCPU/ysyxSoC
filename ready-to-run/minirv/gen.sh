HELLO_BIN=hello-minirv-ysyxsoc.bin
OUT_BIN=$2
OFFSET=369152

magic=`dd if=$HELLO_BIN bs=1 skip=$OFFSET count=4 | hexdump --format '"%x"'`

if [[ $magic != "464c457f" ]]; then
  echo bad magic number
  exit -1
fi

cp $HELLO_BIN $OUT_BIN
dd of=$OUT_BIN if=$1 bs=1 seek=$OFFSET
