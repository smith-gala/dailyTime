from __future__ import annotations

import json
import os
import sys
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import ProxyHandler, Request, build_opener, urlopen


DEFAULT_AGENT_URL = "http://127.0.0.1:8080/api/v1/agent/chat"
DEFAULT_TIMEOUT_SECONDS = 120.0


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")


class AgentClientError(RuntimeError):
    """A request error that is safe to return to the OpenClaw caller."""


def required_environment(name: str) -> str:
    value = os.environ.get(name, "")
    if not value.strip():
        raise AgentClientError(f"缺少环境变量 {name}")
    return value


def build_request() -> Request:
    conversation_id = required_environment("DAILY_TIME_CONVERSATION_ID").strip()
    user_id = required_environment("DAILY_TIME_USER_ID").strip()
    message = required_environment("DAILY_TIME_MESSAGE")
    if not user_id.startswith("ou_"):
        raise AgentClientError("DAILY_TIME_USER_ID 必须是飞书可信元数据中的 ou_ 用户 ID")

    request_body = json.dumps(
        {"conversationId": conversation_id, "message": message},
        ensure_ascii=False,
    ).encode("utf-8")
    endpoint = os.environ.get("DAILY_TIME_AGENT_URL", DEFAULT_AGENT_URL).strip()
    if not endpoint:
        endpoint = DEFAULT_AGENT_URL
    return Request(
        endpoint,
        data=request_body,
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "Accept": "application/json",
            "X-User-Id": user_id,
        },
        method="POST",
    )


def parse_json_response(raw_body: bytes) -> dict[str, Any]:
    try:
        response = json.loads(raw_body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise AgentClientError("Java Agent 返回了无效 JSON") from exc
    if not isinstance(response, dict):
        raise AgentClientError("Java Agent 返回的 JSON 顶层必须是对象")
    return response


def open_agent_request(request: Request, *, timeout: float):
    """Connect directly to the local Java service despite inherited proxies."""
    if urlsplit(request.full_url).hostname in {"localhost", "127.0.0.1", "::1"}:
        return build_opener(ProxyHandler({})).open(request, timeout=timeout)
    return urlopen(request, timeout=timeout)


def forward_message() -> dict[str, Any]:
    request = build_request()
    timeout_text = os.environ.get("DAILY_TIME_AGENT_TIMEOUT", "").strip()
    try:
        timeout = float(timeout_text) if timeout_text else DEFAULT_TIMEOUT_SECONDS
    except ValueError as exc:
        raise AgentClientError("DAILY_TIME_AGENT_TIMEOUT 必须是秒数") from exc
    if timeout <= 0:
        raise AgentClientError("DAILY_TIME_AGENT_TIMEOUT 必须大于 0")

    try:
        with open_agent_request(request, timeout=timeout) as response:
            return parse_json_response(response.read())
    except HTTPError as exc:
        try:
            error_payload = parse_json_response(exc.read())
        except AgentClientError:
            error_payload = {
                "code": "JAVA_AGENT_HTTP_ERROR",
                "message": f"Java Agent HTTP {exc.code}",
            }
        error_payload.setdefault("httpStatus", exc.code)
        return error_payload
    except URLError as exc:
        reason = getattr(exc, "reason", exc)
        raise AgentClientError(f"无法连接 Java Agent：{reason}") from exc
    except TimeoutError as exc:
        raise AgentClientError("调用 Java Agent 超时") from exc


def emit_json(payload: dict[str, Any]) -> None:
    print(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), flush=True)


def main() -> int:
    try:
        result = forward_message()
        emit_json(result)
        return 0 if result.get("code") == "OK" else 1
    except Exception as exc:
        emit_json(
            {
                "code": "JAVA_AGENT_UNAVAILABLE",
                "message": str(exc),
                "data": None,
            }
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
