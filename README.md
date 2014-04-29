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

First, create conf/tokenmgr.yml (see conf/tokenmgr.yml.sample) as
appropriate for your environment. NOTE: if you're using a prefix, it
must begin with '/' in order to work properly

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

    lein run filter import <app-name> <path-to-csv> [--delimiter ,]

## Change Log
* Release 1.1.2 on 2014-04-29
    * Replaced load command with import command
    * Added export command functionality for single application
* Release 1.1.1 on 2014-03-04
    * Executable template files generate executable files
* Release 1.1.0 on 2014-03-03
    * Tokens added with no value produce empty string value, rather
      than null and error during filter
    * Added load command to import tokens from CSV NOTE: now requires
      filter for old functionality


## License

This is released under the EPL (http://www.eclipse.org/legal/epl-v10.html)

Copyright © 2013 One Kings Lane
