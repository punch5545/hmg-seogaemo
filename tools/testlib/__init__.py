"""Shared host tools for tests that execute head-unit scripts."""
import os
from pathlib import Path
import shutil

FAKEBIN = Path(__file__).resolve().parent / "fakebin"
SHELL = os.environ.get("HMG_SEOGAEMO_TEST_SHELL") or shutil.which("mksh") or "/bin/sh"


def car_env(values=None):
    result = dict(os.environ if values is None else values)
    result["PATH"] = f"{FAKEBIN}:{result['PATH']}"
    return result
