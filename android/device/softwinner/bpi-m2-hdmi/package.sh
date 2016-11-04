cd $PACKAGE
DEBUG="uart0"
SIGMODE="none"
VERSION="4.4"
if [ "$1" = "-d" -o "$2" = "-d" ]; then
	echo "--------debug version, have uart printf-------------"
	DEBUG="card0";
else
	echo "--------release version, donnot have uart printf-------------"
fi
if [ "$1" = "-s" -o "$2" = "-s" ]; then
	echo "-------------------sig version-------------------"
	SIGMODE="sig";
fi
	./pack -c sun6i -p android -v ${VERSION} -b bpi-m2-hdmi  -d ${DEBUG} -s ${SIGMODE}

cd -
