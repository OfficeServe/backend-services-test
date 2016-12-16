#!/bin/bash

function usage {
  echo $"Usage: $0 -env (local|dev|testing) -m <file>"
}

while [ "$1" != "" ]
do
key="$1"
case $key in
    -env|--environment)
    ENV="$2"
    shift # past argument
    ;;
    -m|--message)
    MESSAGE_FILE=$2
    shift # past argument
    ;;
    -h|--help)
	usage
	exit 1
    ;;
    *)
            # unknown option
    ;;
esac
shift # past argument or value
done

if [ ! -f $MESSAGE_FILE ]; then
  echo "Message file not found"
  usage
  exit 1
fi

MESSAGE=`cat $MESSAGE_FILE`

case $ENV in
	"local")
		aws sns --endpoint-url http://localhost:9911 publish --topic-arn arn:aws:sns:eu-west-1:064345613152:local_orders  --message "$MESSAGE"
		;;
	"dev")
		aws sns publish --topic-arn arn:aws:sns:eu-west-1:064345613152:dev_orders --message "$MESSAGE"
		;;
	"testing")
		aws sns publish --topic-arn arn:aws:sns:eu-west-1:064345613152:testing_orders --message "$MESSAGE"
		;;
	*)
		echo "Invalid environemnt. Choose from (local|dev|testing)"
    usage
		exit 1
    ;;
esac
