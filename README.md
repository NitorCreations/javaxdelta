# javaxdelta

```
usage:
  target/javaxdelta.sh [-d [port]] delta source.zip target.zip patch.zip
    or
  target/javaxdelta.sh [-d [port]] patch [-ps num] [-po num] patch.zip [target.zip [source.zip]]
    -d         start debugger and wait on defined port (4444 by default)
    -ps num    ingore num path elements on the source entry inside the patch
    -po num    ingore num path elements on the output entry inside the patch
```
