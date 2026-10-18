#!/bin/bash

echo "Unlock 4K 60FPS Cam Recording"
cd p*/a*/Aperture
git fetch https://github.com/Nothing-2A/android_packages_apps_Aperture.git
git cherry-pick 9509277efc852ad8bdcce204e0d9cfe104b6d190
cd ../../..

echo "Exporting prebuilt headers path"
export TARGET_PREBUILT_KERNEL_HEADERS=device/xiaomi/duchamp-kernel/kernel-uapi-headers.tar.gz
