#!/bin/bash

NAME="ACRA-Receiver"
ZIPFILE="code.zip"

npm install

rm "$ZIPFILE"
zip -r "$ZIPFILE" node_modules *.js
echo

echo "Uploading ${ZIPFILE} to ${NAME}"
aws lambda update-function-code --function-name ${NAME} --zip-file "fileb://${ZIPFILE}"
echo

echo "Testing ${NAME}"
aws lambda invoke --invocation-type RequestResponse --function-name ${NAME} --payload file://test-input.json test-output.json
cat test-output.json
echo
