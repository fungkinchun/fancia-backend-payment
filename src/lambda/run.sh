#!/bin/sh
cd "${LAMBDA_TASK_ROOT:-.}"
exec java -jar app.jar
