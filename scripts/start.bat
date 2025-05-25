@echo off
echo 🚀 Starting Camel Groovy Validator...

if not exist data mkdir data
if not exist data\input mkdir data\input
if not exist data\output mkdir data\output
if not exist data\error mkdir data\error
if not exist logs mkdir logs

groovy run.groovy
pause