#!/usr/bin/tcsh

set DEV_HOME = /home/dev
set SRC      = /home/dev/src/planetarium/java
set BIN      = /home/dev/bin/planetarium/java
set CFG      = /home/dev/src/planetarium/config
set SCRIPT   = /home/dev/src/planetarium/scripts
set DEPLOY   = /home/dev/src/planetarium/tmp

pushd `pwd`

cd $DEPLOY
echo in `pwd`

rm -rf *

mkdir lib
mkdir class
mkdir scripts
mkdir config
mkdir docs
mkdir src

cd `popd`
echo in `pwd`

# Collect all JARs from javalib.
set JAVA_LIB = $DEV_HOME/bin/javalib

echo "*** Copying NGAT Libraries."
cp  $JAVA_LIB/ngat_util.jar $DEPLOY/lib/
cp  $JAVA_LIB/ngat_util_logging.jar $DEPLOY/lib/
cp  $JAVA_LIB/ngat_util_charting.jar $DEPLOY/lib/
cp  $JAVA_LIB/ngat_net.jar $DEPLOY/lib/
cp  $JAVA_LIB/ngat_astrometry.jar $DEPLOY/lib/
cp  $JAVA_LIB/ngat_instrument.jar $DEPLOY/lib/
cp  $JAVA_LIB/ngat_math.jar $DEPLOY/lib/
cp  $JAVA_LIB/ngat_message_base.jar $DEPLOY/lib/
cp  $JAVA_LIB/ngat_message_pos_rcs.jar $DEPLOY/lib/
cp  $JAVA_LIB/ngat_message_rcs_tcs.jar $DEPLOY/lib/
cp  $JAVA_LIB/ngat_message_iss_inst.jar $DEPLOY/lib/
cp  $JAVA_LIB/dev_lt.jar $DEPLOY/lib/

echo "*** Copying source files."
# Collect source (except POS_CommandRelay.java).
cp  $SRC/POS*.java $DEPLOY/src/
cp  /home/dev/src/ngat/net/SSLFileTransfer.java $DEPLOY/src/
rm $DEPLOY/src/POS_CommandRelay.java

echo "*** Copying class files."
# Collect class files.
cp  $BIN/POS*.class $DEPLOY/class/

echo "*** Copying config files."
# Collect properties.
cp   $CFG/*.properties $DEPLOY/config/

echo "*** Copying scripts."
# Collect scripts.
cp   $SCRIPT/* $DEPLOY/scripts/

echo "*** Copying external documentation."
cp  /home/dev/src/rcs/latex/icd.ps $DEPLOY/docs/

echo "*** Jarring all."
# Tar and Jar all stuff
set day = `date -u '+%y%j%H%M' `
cd $DEPLOY
tar cvf install_${day}.tar *
mv install_${day}.tar ../
jar cvf install_${day}.jar *
mv ../install_${day}.tar .
gzip install_${day}.tar





