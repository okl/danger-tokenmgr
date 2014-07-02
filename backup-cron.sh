#!/bin/bash
# backup-cron.sh: Backs up tokens to S3 when called

# TODO ensure that the correct directory is used

NOW=$(date +'%Y%m%d%H%M%S')

YEAR=$(date +'%Y')
MONTH=$(date +'%m')
DAY=$(date +'%d')

FILENAME="tokenmgr-backup-$NOW.json"

S3_BUCKET="okl-ansible-tokenmgr-backup"
S3_ENV="prod"
S3_HOST="dancible.newokl.com"
S3_PATH="/opt/danger-tokenmgr"

lein run backup "$FILENAME"
BK_OUT=$?

if [ -f "$FILENAME" -a $BK_OUT == 0 ] ; then
	# If file is present, upload to S3
	s3cmd put $FILENAME\
 "s3://$S3_BUCKET/$YEAR/$MONTH/$DAY/$S3_ENV/$S3_HOST$S3_PATH/$FILENAME"
	UP_OUT=$?
	if [ $UP_OUT != 0 ] ; then
		STATUS=1
		STATUS_MSG="File failed to upload to S3."
	else
		STATUS=0
	fi
	rm -f "$FILENAME"
else
	STATUS=1
	STATUS_MSG="Tokenmgr failed to export to file."
fi

if [ $STATUS != 0 ] ; then
	# Panic and shoot email.
	mail -s "Tokenmgr Backup Failed" danger-fails@onekingslane.com <<EMAIL
Tokenmgr backup failed to backup: $STATUS_MSG
EMAIL
fi

exit $STATUS
