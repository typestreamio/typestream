#!/usr/bin/env python3
"""Simple HTTP server that proxies gRPC state query for chart visualization."""

import json
import subprocess
from http.server import HTTPServer, SimpleHTTPRequestHandler
import os

STORE_NAME = os.environ.get("STORE_NAME", "ecd63e64-57ac-4f7f-8090-25a8b9356adf-reduce-store-0")
GRPC_HOST = os.environ.get("GRPC_HOST", "localhost:8080")


class ChartHandler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/api/prices":
            self.send_prices()
        elif self.path == "/" or self.path == "/index.html":
            self.path = "/index.html"
            return SimpleHTTPRequestHandler.do_GET(self)
        else:
            return SimpleHTTPRequestHandler.do_GET(self)

    def send_prices(self):
        try:
            result = subprocess.run(
                [
                    "grpcurl", "-plaintext",
                    "-d", json.dumps({"store_name": STORE_NAME, "limit": 20}),
                    GRPC_HOST,
                    "io.typestream.grpc.StateQueryService/GetAllValues"
                ],
                capture_output=True,
                text=True,
                timeout=5
            )

            prices = []
            # grpcurl outputs multiple JSON objects, split on }\n{
            raw = result.stdout.strip()
            if raw:
                # Split multiple JSON objects (handle pretty-printed output)
                json_strs = raw.replace("}\n{", "}\x00{").split("\x00")
                for json_str in json_strs:
                    try:
                        item = json.loads(json_str)
                        key = json.loads(item.get("key", "{}"))
                        value = json.loads(item.get("value", "{}"))
                        prices.append({
                            "product_id": key.get("product_id", value.get("product_id", "unknown")),
                            "price": float(value.get("price", 0)),
                            "volume_24h": float(value.get("volume_24h", 0)),
                            "high_24h": float(value.get("high_24h", 0)),
                            "low_24h": float(value.get("low_24h", 0)),
                            "timestamp": value.get("timestamp", ""),
                        })
                    except (json.JSONDecodeError, ValueError):
                        continue

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps(prices).encode())

        except Exception as e:
            self.send_response(500)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"error": str(e)}).encode())

    def log_message(self, format, *args):
        print(f"[{self.log_date_time_string()}] {format % args}")


if __name__ == "__main__":
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    port = int(os.environ.get("PORT", 8081))
    server = HTTPServer(("", port), ChartHandler)
    print(f"Chart server running at http://localhost:{port}")
    print(f"Store: {STORE_NAME}")
    server.serve_forever()
