"""Quick Python mirror of core/Physics.kt for level design iteration (not shipped).

Usage: python3 levellab.py  -> prints each draft level's path with ASCII top (x-z) and side (z-y) views.
"""
import math, random, sys

DT = 1/360
GAIN = float(sys.argv[1]) if len(sys.argv) > 1 else 2.4
PROBE_R = 0.018
BOUNDS = ((-1.3, -0.75, -1.3), (1.3, 0.85, 0.7))
LAUNCH = (0.0, -0.15, 0.35)

def add(a, b): return (a[0]+b[0], a[1]+b[1], a[2]+b[2])
def sub(a, b): return (a[0]-b[0], a[1]-b[1], a[2]-b[2])
def mul(a, s): return (a[0]*s, a[1]*s, a[2]*s)
def dist(a, b): return math.sqrt(sum((x-y)**2 for x, y in zip(a, b)))
def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
def norm(a):
    l = math.sqrt(sum(x*x for x in a)); return mul(a, 1/l)

class Body:
    def __init__(s, id, pos, r, mu, orbit=None):
        s.id, s.pos, s.r, s.mu, s.orbit = id, pos, r, mu, orbit
        if orbit:
            c, R, per, ph = orbit[:4]
            n = norm(orbit[4]) if len(orbit) > 4 else (0, 1, 0)
            ref = (1, 0, 0) if abs(n[0]) < 0.9 else (0, 0, 1)
            u = norm(cross(cross(n, ref), n)); w = cross(n, u)
            s.c, s.u, s.w, s.om, s.ph = c, mul(u, R), mul(w, R), 2*math.pi/per, math.radians(ph)
    def at(s, t):
        if not s.orbit: return s.pos
        a = s.ph + s.om*t
        return add(s.c, add(mul(s.u, math.cos(a)), mul(s.w, math.sin(a))))

def fly(bodies, pull, tmax, launch=LAUNCH, every=36, bounds=BOUNDS, boosters=(), portals=()):
    """boosters: [(pos, radius, factor)], portals: [((a_pos, a_radius), b_pos)] (same rules as Kotlin)."""
    v = mul(pull, -GAIN); x = launch; t = 0.0
    used_b = [False] * len(boosters); used_p = [False] * len(portals)
    events = []
    def acc(x, t):
        a = (0, 0, 0)
        for b in bodies:
            if b.mu == 0: continue
            d = sub(b.at(t), x); r = math.sqrt(sum(q*q for q in d))
            a = add(a, mul(d, b.mu/r**3))
        return a
    a = acc(x, t); path = [(t, x)]; n = 0; status = 'FLY'
    while t < tmax:
        x = add(x, add(mul(v, DT), mul(a, 0.5*DT*DT)))
        t += DT; a2 = acc(x, t); v = add(v, mul(add(a, a2), 0.5*DT)); a = a2; n += 1
        for b in bodies:
            if dist(x, b.at(t)) < b.r + PROBE_R: status = 'CRASH:'+b.id
        if status == 'FLY':
            for i, (bp, br, bf) in enumerate(boosters):
                if not used_b[i] and dist(x, bp) <= br:
                    used_b[i] = True; v = mul(v, bf); events.append((t, 'boost', i))
            for i, ((ap, ar), bpos) in enumerate(portals):
                if not used_p[i] and dist(x, ap) <= ar:
                    used_p[i] = True; x = bpos; a = acc(x, t); events.append((t, 'warp', i))
        mn, mx = bounds
        if not all(mn[i] <= x[i] <= mx[i] for i in range(3)): status = 'LOST'
        if n % every == 0 or status != 'FLY': path.append((t, x))
        if status != 'FLY': break
    fly.events = events
    return status, path

def plot(bodies, path, marks=()):
    W, H = 52, 22
    def grid(ax, ay, rx, ry, title):
        g = [[' ']*W for _ in range(H)]
        def put(p, ch):
            i = int((p[ax]-rx[0])/(rx[1]-rx[0])*(W-1)); j = int((ry[1]-p[ay])/(ry[1]-ry[0])*(H-1))
            if 0 <= i < W and 0 <= j < H: g[j][i] = ch
        for b in bodies:
            p0 = b.at(0)
            put(p0, b.id[0].upper())
        for _, p in path: put(p, '.')
        for p, ch in marks: put(p, ch)
        put(LAUNCH, 'L')
        return [title] + [''.join(r) for r in g]
    top = grid(0, 2, (-1.3, 1.3), (-1.3, 0.7), 'TOP x→ z↑(far=down)')
    # flip so far(-z) is at top: use -z as y
    top = grid(0, 2, (-1.3, 1.3), (-1.3, 0.7), 'TOP (x right, near player at top)')
    side = grid(2, 1, (-1.3, 0.7), (-0.75, 0.85), 'SIDE (z right=near player, y up)')
    for a, b in zip(top, side): print(a.ljust(W+2) + '|' + b)

def report(name, bodies, pull, tgoal, tstars=(), tcp=(), launch=LAUNCH, show=True, boosters=(), portals=()):
    st, path = fly(bodies, pull, tgoal, launch, boosters=boosters, portals=portals)
    ev = list(fly.events)
    def at(tq):
        for t, p in path:
            if t >= tq - 1e-9: return p
        return path[-1][1]
    marks = [(at(t), '*') for t in tstars] + [(at(t), 'C') for t in tcp] + [(at(tgoal), 'G')]
    marks += [(b[0], '+') for b in boosters] + [(p[0][0], 'O') for p in portals] + [(p[1], 'X') for p in portals]
    md = {b.id: round(min(dist(p, b.at(t)) - b.r for t, p in path), 3) for b in bodies}
    print(f"== {name} status={st} end_t={path[-1][0]:.2f} goal={tuple(round(c,2) for c in at(tgoal))} events={[(round(t,2),k) for t,k,_ in ev]} clearance={md}")
    if show: plot(bodies, path, marks)
    return st, path

def robust(bodies, pull, tgoal, goal, gr=0.075, jitter=0.015, n=150, seed=5, boosters=(), portals=()):
    rng = random.Random(seed); ok = 0
    for _ in range(n):
        u = rng.uniform(-1, 1); th = rng.uniform(0, 2*math.pi); s = math.sqrt(1-u*u)
        r = jitter * rng.random() ** (1/3)
        p = add(pull, mul((s*math.cos(th), s*math.sin(th), u), r))
        st, path = fly(bodies, p, tgoal + 6, every=1, boosters=boosters, portals=portals)
        if any(dist(q, goal) < gr for _, q in path): ok += 1
    return ok / n

def at_time(path, tq):
    for t, p in path:
        if t >= tq - 1e-9: return p
    return path[-1][1]

def hash01(k):
    """Deterministic pseudo-random in [0, 1); identical formula in Levels.kt."""
    v = math.sin(k * 12.9898 + 78.233) * 43758.5453
    return v - math.floor(v)

def belt(plane_z, gap_center, gap_r, xs, ys, r=0.035, jitter=0.03, seed=3, prefix='rock'):
    """Asteroid wall on z = plane_z over a grid, leaving a hole of radius gap_r around gap_center (x, y)."""
    out = []; k = 0; n = 0
    for x in xs:
        for y in ys:
            h = seed * 1000 + n * 3; n += 1
            px = x + (hash01(h) * 2 - 1) * jitter
            py = y + (hash01(h + 1) * 2 - 1) * jitter
            pz = plane_z + (hash01(h + 2) * 2 - 1) * jitter
            if math.hypot(px - gap_center[0], py - gap_center[1]) < gap_r: continue
            out.append(Body(f'{prefix}{k}', (px, py, pz), r, 0.0)); k += 1
    return out

def frange(a, b, n):
    return [a + (b - a) * i / (n - 1) for i in range(n)]

def aim_at(bodies, target, mag, iters=25, launch=LAUNCH, tmax=6, **mech):
    """Shooting method: pull of length `mag` whose path passes closest to `target`. Returns (pull, miss)."""
    d = norm(sub(launch, target))  # pull points away from the target
    for _ in range(iters):
        pull = mul(d, mag)
        st, path = fly(bodies, pull, tmax, launch, every=1, **mech)
        t, q = min(path, key=lambda tq: dist(tq[1], target))
        err = sub(target, q)
        if dist(q, target) < 1e-3: break
        # nudge the launch direction opposite to the miss
        d = norm(sub(d, mul(err, 0.8)))
    return pull, dist(q, target)
