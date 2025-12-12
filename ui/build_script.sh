#!/usr/bin/env bash
ENV="prod"
ls -alp

if [ "$ENV" = "prod" ];then
    sed -i "s?__MUSES_HOSTNAME__?https://muses.services.ding.unisannio.it?g" assets/js/shared.js
else
    sed -i "s?__MUSES_HOSTNAME__?https://muses.dev.services.ding.unisannio.it?g" assets/js/shared.js
fi

cat assets/js/shared.js

# 41.130723,14.7771293 P14zzaR0m4123