import importlib.util
from pathlib import Path


path = Path(__file__).resolve().parents[1] / "conecte_push_gateway.py"
spec = importlib.util.spec_from_file_location("conecte_push_gateway", path)
gateway = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(gateway)
