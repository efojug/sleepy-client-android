SKIPMOUNT=true
PROPFILE=false
POSTFSDATA=false
LATESTARTSERVICE=true

set_perm_recursive $MODPATH 0 0 0777 0777

cp $MODPATH/config.cfg /data/adb/modules/sleepy/config.cfg
echo "配置文件在"
echo "/data/adb/modules/sleepy/config.cfg"