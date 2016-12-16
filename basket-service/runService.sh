#!/usr/bin/env bash
sbt dockerComposeStop dockerComposeUp "project app" run
