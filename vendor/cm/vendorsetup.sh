# for combo in $(wget -o /dev/null -O - https://raw.github.com/LineageOS/hudson/b9f837c447788d4d8cc1c291ee70e92bf4c4e722/cm-build-targets | grep ics | awk {'print $1'}); do echo add_lunch_combo $combo; done
# add_lunch_combo cm_aoba-userdebug
# add_lunch_combo cm_c800-userdebug
# add_lunch_combo cm_doubleshot-userdebug
# add_lunch_combo cm_e400-userdebug
# add_lunch_combo cm_e730-userdebug
# add_lunch_combo cm_e739-userdebug
# add_lunch_combo cm_hikari-userdebug
# add_lunch_combo cm_nozomi-userdebug
# add_lunch_combo cm_p5-userdebug
# add_lunch_combo cm_p5wifi-userdebug
# add_lunch_combo cm_shooteru-userdebug
# add_lunch_combo cm_tenderloin-userdebug
# add_lunch_combo cm_zeus-userdebug
# add_lunch_combo cm_zeusc-userdebug

# ./vendor/cm/get-prebuilts
# mkdir -pv $(pwd)/vendor/cm/proprietary
# curl -L -o $(pwd)/vendor/cm/proprietary/Term.apk -O -L http://jackpal.github.io/Android-Terminal-Emulator/downloads/Term-1.0.65.apk
# unzip -o -d $(pwd)/vendor/cm/proprietary $(pwd)/vendor/cm/proprietary/Term.apk lib/armeabi/libjackpal-androidterm4.so
# ./vendor/cm/get-prebuilts
