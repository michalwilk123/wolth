#!/usr/bin/env python3

from invoke import UnexpectedExit, task
import pathlib
import shlex
import asyncio


async def run_unit_tests(ctx):
    print("Running unit tests")

async def run_sys_tests(ctx, paths:str=""):
    # TODO: ADD OPTION TO NOT BUILD SERVER FROM SCRATCH
    ctx.run("lein run &> /dev/null")
    ctx.run(shlex.join(["pytest", paths]))
    

@task
def check_for_zprint(c):
    try:
        c.run("which zprint &> /dev/null")
    except UnexpectedExit:
        c.run("echo zprint not installed!")
        exit(1)

@task(check_for_zprint)
def lint(c, no_write=False):
    """
    Lints and check all clojure and edn files in the project
    """
    extension_to_lint = ["clj", "edn"]
    fpaths = []

    for ext in extension_to_lint:
        fpaths += list(pathlib.Path("./src").rglob(f"*.{ext}"))
    
    fpaths = [str(fpath) for fpath in fpaths]

    if no_write:
        print("Testing formatting")
        flags = "-lfsc"
    else:
        flags = "-lfsw"

    cmd = ["zprint", flags] + fpaths
    c.run(shlex.join(cmd))

@task
def utest(c):
    ...

@task(iterable=["apps"])
def stest(c, apps, unit=False, system=False):
    """
    Running tests
    """
    tasks = []
    print(f"{apps=}")

    if unit:
        tasks.append(run_unit_tests(c))
    
    if system:
        tasks.append(run_sys_tests(c))
    
    async def __amain(tasks):
        await asyncio.gather(*tasks)

    asyncio.run(__amain(tasks))

@task
def repl(c):
    """
    Running simple REPL. Can be helpful when you want to show something
    without an IDE. Note that nothing (the server or literally anything)
    is getting evaluated
    """
    ...

@task
def run(c):
    """
    Just run the application. Note that this run is not optimized at all!
    Clojure has a long startup time
    """
    ...