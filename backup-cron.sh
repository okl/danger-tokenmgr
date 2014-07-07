#!/bin/bash
# backup-cron.sh: Backs up tokens to S3 when called

# TODO ensure that the correct directory is used

SCRIPT_HOME=$(dirname $(readlink -f -- "$0"))
NOW=$(date +'%Y%m%d%H%M%S')

YEAR=$(date +'%Y')
MONTH=$(date +'%m')
DAY=$(date +'%d')

TMP=$(mktemp -d --tmpdir tokenmgr-backup.XXXXXX)
FILENAME="tokenmgr-backup-$NOW.json"
FILEPATH="$TMP/$FILENAME"

# Cleanup on exit
cleanup () {
	rm -rf "$TMP"
}

trap cleanup EXIT

S3_BUCKET="okl-ansible-tokenmgr-backup"
S3_ENV="prod"
S3_HOST="dancible.newokl.com"
S3_PATH="/opt/danger-tokenmgr"

# CD so lein has correct context
cd $SCRIPT_HOME
/usr/local/bin/lein run backup "$FILEPATH" > $TMP/backup.tm-output
BK_OUT=$?

if [ -f "$FILEPATH" -a $BK_OUT == 0 ] ; then
	# If file is present, upload to S3
	s3cmd put $FILEPATH\
 "s3://$S3_BUCKET/$YEAR/$MONTH/$DAY/$S3_ENV/$S3_HOST$S3_PATH/$FILENAME"\
 > $TMP/backup.s3-output
	UP_OUT=$?
	if [ $UP_OUT != 0 ] ; then
		STATUS=2
		STATUS_MSG="File failed to upload to S3."
	else
		STATUS=0
	fi

else
	STATUS=1
	STATUS_MSG="Tokenmgr failed to export to file."
fi

if [ $STATUS != 0 ] ; then
	# Panic and shoot email.
	cat > $TMP/backup.email <<EMAIL
Tokenmgr backup failed to backup: $STATUS_MSG

EMAIL
	case $STATUS IN
		1)
			cat $TMP/backup.tm-output >> $TMP/backup.email
			;;
		2)
			cat $TMP/backup.s3-output >> $TMP/backup.email
			;;
	esac

	mail -s "Tokenmgr Backup Failed" danger-fails@onekingslane.com\
< $TMP/backup.email
fi

exit $STATUS
