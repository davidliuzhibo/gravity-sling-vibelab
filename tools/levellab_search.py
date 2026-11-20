from levellab import *  # grid search over pulls that reach a goal
import itertools
def search(bodies, goal, gr=0.075, tmax=8, launch=LAUNCH, step=0.02, top=5, require=None):
    res = []
    rng = [i*step for i in range(-int(0.25/step), int(0.25/step)+1)]
    for px, py, pz in itertools.product(rng, rng, [i*step for i in range(0, int(0.25/step)+1)]):
        p = (px, py, pz); m = math.sqrt(px*px+py*py+pz*pz)
        if m < 0.05 or m > 0.25: continue
        st, path = fly(bodies, p, tmax, launch, every=4)
        hit = next((t for t, q in path if dist(q, goal) < gr), None)
        if hit is None: continue
        if require and not require(path, hit): continue
        res.append((hit, p))
    return res
