#!/bin/bash
PIDS=$(ps -wwfe | grep dk.netarkivet.harvester.harvesting.HarvestControllerApplication | grep -v grep | grep /home/test/UNITTEST/conf/settings_harvester_8082.xml | awk "{print \$2}")
if [ -n "$PIDS" ] ; then
    kill -9 $PIDS
fi
PIDS=$(ps -wwfe | grep org.archive.crawler.Heritrix | grep -v grep | grep /home/test/UNITTEST/conf/settings_harvester_8082.xml | awk "{print \$2}")
if [ -n "$PIDS" ] ; then
    kill -9 $PIDS
fi