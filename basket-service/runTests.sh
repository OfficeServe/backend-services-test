#!/usr/bin/env bash
sbt clean test:compile dockerComposeStop dockerComposeUp test dockerComposeStop
