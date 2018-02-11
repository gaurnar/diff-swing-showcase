# diff-swing-showcase

Simple Java Swing diff program written by me for job application. Do not expect much from it ;)

## Features

- Side-by-side comparison of text files
- Uses [Myers](https://neil.fraser.name/writing/diff/myers.pdf) algorithm for char by char comparison
- Currently supports only UTF-8 or ASCII text files

## TODO

- File editing
- Toggle display of equal parts of files (hide/show)
- Work on heuristics for prettier diff display (as in e.g. IntelliJ IDEA)
- Implement tests
- Support non-ASCII encodings, UTF-16 etc.

## How to build

This repository contains IntelliJ IDEA project. You have to use it to build JAR artifact (or just download it from releases).
