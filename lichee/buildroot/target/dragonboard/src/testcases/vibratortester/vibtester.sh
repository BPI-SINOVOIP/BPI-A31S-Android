#!/bin/sh

source send_cmd_pipe.sh
source script_parser.sh

module_count=`script_fetch "vibrator" "module_count"`
if [ $module_count -gt 0 ]; then
    for i in $(seq $module_count); do
        key_name="module"$i"_path"
        module_path=`script_fetch "vibrator" "$key_name"`
        if [ -n "$module_path" ]; then
            echo "insmod $module_path"
            insmod "$module_path"
            if [ $? -ne 0 ]; then
                SEND_CMD_PIPE_FAIL $3
                exit 1
            fi
        fi
    done
fi

sleep 3

device_name=`script_fetch "vibrator" "device_name"`
cd /sys/class/timed_output/$device_name
while :
do
echo "1000" > enable
sleep 2
done

#SEND_CMD_PIPE_FAIL $3
