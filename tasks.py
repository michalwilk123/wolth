#!/usr/bin/env python3

from invoke import task


@task
def test():
    print("hello world")
