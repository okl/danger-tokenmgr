# danger-tokenmgr

This is an application designed to manage token replacement for
configuration files. This project uses zookeeper as a
backend. There's a compojure app designed for managing the values of
the tokens, and a command line driver for performing the replacement.

## Prerequisites

You will need [Leiningen][1] 1.7.0 or above installed.

You also need to have [zookeeper][2] installed, configured, and
running.
[1]: https://github.com/technomancy/leiningen
[2]: http://zookeeper.apache.org

## Running

First, edit conf/tokenmgr.yml as appropriate for your environment.

NOTE: If you're using a prefix, then it must begin with '/' in order to work
      properly.

Next you must properly initialize the SlickGrid submodule. In the top-level directory, run:
    git submodule init
    git submodule update

To start a web server for the application, run:

    lein ring server

This allows you to run the UI and manage tokens and values.

To run the command line replacement driver run:

    lein run filter <application-name> <environment> <directory to filter> [--token "TOKEN_NAME=TOKEN_VALUE"]

Now we can also import from csv sources new tokens. The csv is
expected to have a header row with the token_key (name of token) and
description columns, as well as one column per environment. By default
the delimiter is tab, but with --delimiter you should be able to
change it to whatever you want

    lein run import <app-name> <path-to-csv> [--delimiter ,]

## License

This is released under the EPL (http://www.eclipse.org/legal/epl-v10.html)

Copyright © 2013 One Kings Lane
