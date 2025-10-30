# Inherit common CM stuff
$(call inherit-product, vendor/cyanogen/products/common_full_no_themes.mk)

PRODUCT_COPY_FILES +=  \
    vendor/cyanogen/prebuilt/hdpi/media/bootanimation.zip:system/media/bootanimation.zip

# Theme packages
include vendor/cyanogen/products/themes.mk
