# asar

[![CircleCI](https://circleci.com/gh/tatut/asar.svg?style=svg)](https://circleci.com/gh/tatut/asar)

Read ASAR archive files from Clojure

See: https://github.com/electron/asar

## Usage

The API is very simple. First you `load-asar` from a file. This returns a handle containing a `RandomAccessFile`
and the parsed JSON header.

To access files, call `read-file`, `copy` and `file-info` functions with the previously loaded ASAR handle and the path
within the archive.

## Ring handler

The library contains a Ring handler to serve files from an ASAR archive.
Check out `serve.sh` script to try it locally.
