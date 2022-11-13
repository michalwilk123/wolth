#!/bin/bash

# install python deps
pip3 install -r requirements.txt

test_person(){
    sleep 10
    python3 -m pytest test/system/person/test_person_app.py 
    pkill java
}

# execute tests
test_person &

# start the clojure server with the person app instance
lein run run test/system/person/person.app.edn
