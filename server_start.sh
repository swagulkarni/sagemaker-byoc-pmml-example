#!/bin/bash


freeMemory=$(awk '/MemFree/ { printf "%.3f \n", $2 }' /proc/meminfo)

echo $freeMemory

echo "[server-startup] Starting java application"

exec java -Djava.security.egd=file:/dev/./urandom -Dapp.port=8080 -jar /work/app.jar -Xmx$maxHeapMemory

