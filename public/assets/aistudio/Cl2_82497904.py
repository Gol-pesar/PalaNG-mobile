#!/usr/bin/env python3
import asyncio, socket, struct, sys, os, hmac, hashlib
import websockets

try:
    import uvloop
    asyncio.set_event_loop_policy(uvloop.EventLoopPolicy())
except ImportError:
    pass

UUID        = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
WORKER_HOST = "twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev"
WORKER_URL  = f"wss://{WORKER_HOST}/"
CLEAN_IP    = "172.67.70.244"
LISTEN      = ("127.0.0.1", 40446)
SUBPROTO    = "chat.v2"
MAX_CHUNK   = 65536
BUFFER_MAX  = 4194304  # 4MB

_WS_MAJOR = int(websockets.__version__.split(".")[0]) if hasattr(websockets, "__version__") else 12
_HEADERS_KWARG = "additional_headers" if _WS_MAJOR >= 14 else "extra_headers"

BO = sys.byteorder
_int_from_bytes = int.from_bytes

def derive_key(nonce):
    tb = UUID.encode()
    return bytes(tb[i % len(tb)] ^ nonce[i] for i in range(16))

def build_init(host, port, payload=b""):
    nonce = os.urandom(16)
    key = derive_key(nonce)
    try:
        socket.inet_pton(socket.AF_INET, host); at, addr = 1, socket.inet_aton(host)
    except OSError:
        try:
            socket.inet_pton(socket.AF_INET6, host); at, addr = 3, socket.inet_pton(socket.AF_INET6, host)
        except OSError:
            hb = host.encode(); at, addr = 2, bytes([len(hb)]) + hb
    body = bytes([1]) + struct.pack("!H", port) + bytes([at]) + addr + payload
    
    init_key_expanded = key * (len(body) // len(key)) + key[:len(body) % len(key)]
    enc = (_int_from_bytes(body, BO) ^ _int_from_bytes(init_key_expanded, BO)).to_bytes(len(body), BO)
    return nonce + enc + hmac.new(key, enc, hashlib.sha256).digest()[:8], key

async def ws_connect():
    kw = {
        "max_size": None,
        "open_timeout": 15,
        "subprotocols": [SUBPROTO],
        "server_hostname": WORKER_HOST,
        "compression": None,
        "ping_interval": None,
    }
    if CLEAN_IP:
        ip = f"[{CLEAN_IP}]" if ":" in CLEAN_IP else CLEAN_IP
        url = WORKER_URL.replace(WORKER_HOST, ip)
        kw[_HEADERS_KWARG] = {"Host": WORKER_HOST}
        try:
            return await websockets.connect(url, **kw)
        except Exception:
            pass
    
    kw[_HEADERS_KWARG] = {}
    return await websockets.connect(WORKER_URL, **kw)

async def handler(r, w):
    try:
        try:
            h = await asyncio.wait_for(r.readexactly(2), timeout=5)
            if h[0] != 5: return
            await asyncio.wait_for(r.readexactly(h[1]), timeout=5)
        except Exception: return
            
        w.transport.write(b"\x05\x00")
        
        try:
            q = await asyncio.wait_for(r.readexactly(4), timeout=5)
            if q[0] != 5 or q[1] != 1: return
            at = q[3]
            if at == 1: host = socket.inet_ntoa(await r.readexactly(4))
            elif at == 3: host = (await r.readexactly((await r.readexactly(1))[0])).decode()
            elif at == 4: host = socket.inet_ntop(socket.AF_INET6, await r.readexactly(16))
            else: return
            port = struct.unpack("!H", await r.readexactly(2))[0]
        except Exception: return

        # بهینه‌سازی‌های عمیق سیستم‌عامل
        sock = w.get_extra_info('socket')
        if sock:
            try:
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                if hasattr(socket, 'TCP_QUICKACK'): # حذف تاخیر ACK در لینوکس
                    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_QUICKACK, 1)
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, BUFFER_MAX) 
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, BUFFER_MAX) 
            except Exception: pass

        try:
            ws = await ws_connect()
        except Exception: return

        init, key = build_init(host, port)
        await ws.send(init)
        
        raw_expanded = key * ((MAX_CHUNK // len(key)) + 1)
        mv_key = memoryview(raw_expanded)
        key_cache = {}
        
        try:
            ack = await asyncio.wait_for(ws.recv(), timeout=10)
            if len(ack) < 16: return
        except Exception: return
        
        # هندل کردن سریع Handshake
        ack_l = len(ack)
        ack_k = _int_from_bytes(mv_key[:ack_l], BO)
        expect = hmac.new(key, b"OK", hashlib.sha256).digest()[:16]
        if (_int_from_bytes(ack, BO) ^ ack_k).to_bytes(ack_l, BO) != expect: return

        w.transport.write(b"\x05\x00\x00\x01\x00\x00\x00\x00\x00\x00")

        # --- حلقه‌های داده با اینلاین کردن کامل لاجیک رمزنگاری (صفر Function Call Overhead) ---
        async def up():
            r_read = r.read
            ws_send = ws.send
            try:
                while True:
                    d = await r_read(MAX_CHUNK)
                    if not d: break
                    l = len(d)
                    k = key_cache.get(l)
                    if k is None:
                        k = _int_from_bytes(mv_key[:l], BO)
                        if len(key_cache) < 2048: key_cache[l] = k
                    await ws_send((_int_from_bytes(d, BO) ^ k).to_bytes(l, BO))
            except Exception: pass
            finally:
                try: await ws.close()
                except: pass
                
        async def down():
            transport = w.transport
            t_write = transport.write
            w_drain = w.drain
            get_buf = transport.get_write_buffer_size
            try:
                async for m in ws:
                    if m:
                        l = len(m)
                        k = key_cache.get(l)
                        if k is None:
                            k = _int_from_bytes(mv_key[:l], BO)
                            if len(key_cache) < 2048: key_cache[l] = k
                        
                        # ریختن مستقیم در لایه C ترنسپورت
                        t_write((_int_from_bytes(m, BO) ^ k).to_bytes(l, BO))
                        
                        if get_buf() > BUFFER_MAX:
                            await w_drain()
            except Exception: pass
            finally:
                try: w.close()
                except: pass
                
        await asyncio.gather(up(), down())
    except Exception: pass
    finally:
        try: w.close()
        except: pass

async def main():
    print(f"[*] ULTRA-Fast SOCKS5 Proxy 127.0.0.1:40446 → {WORKER_HOST}")
    srv = await asyncio.start_server(handler, LISTEN[0], LISTEN[1])
    async with srv: await srv.serve_forever()

if __name__ == "__main__":
    try: asyncio.run(main())
    except KeyboardInterrupt: pass
