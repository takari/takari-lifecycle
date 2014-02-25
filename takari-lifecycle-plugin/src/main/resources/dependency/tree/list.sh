#!/bin/sh

find assets -type f | sed 's/^.\///' > resources.txt
