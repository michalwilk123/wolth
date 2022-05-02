#!/usr/bin/env python3

from invoke import task


@task
def test_invoke(c):
    print("hello world")

@task
def test(c, unit_test=False, system_tests=False):
    if unit_test:
        c.run("echo 'Running unittests'")
    
    if system_tests:
        c.run("echo 'Running system tests'")
    
    if not (unit_test or system_tests):
        c.run("echo {}".format("You have not specified what tests do you want to run"))
