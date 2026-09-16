from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread
from urllib.request import Request

from scripts import daily_time_agent


def test_local_agent_bypasses_broken_inherited_proxy(monkeypatch):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b'{"code":"OK"}')

        def log_message(self, *_args):
            pass

    # Reserve a local port without listening: any attempted proxy use must fail.
    import socket

    with socket.socket() as broken_proxy:
        broken_proxy.bind(("127.0.0.1", 0))
        monkeypatch.setenv("http_proxy", f"http://127.0.0.1:{broken_proxy.getsockname()[1]}")
        monkeypatch.setenv("no_proxy", "")
        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            for hostname in ("127.0.0.1", "localhost"):
                request = Request(f"http://{hostname}:{server.server_port}/health")
                with daily_time_agent.open_agent_request(request, timeout=2) as response:
                    assert response.status == 200
                    assert response.read() == b'{"code":"OK"}'
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)


def test_remote_agent_preserves_default_proxy_handling(monkeypatch):
    captured = {}
    expected = object()

    def fake_urlopen(request, *, timeout):
        captured.update(request=request, timeout=timeout)
        return expected

    monkeypatch.setattr(daily_time_agent, "urlopen", fake_urlopen)
    request = Request("https://agent.example.test/api/v1/agent/chat")
    assert daily_time_agent.open_agent_request(request, timeout=12) is expected
    assert captured == {"request": request, "timeout": 12}
