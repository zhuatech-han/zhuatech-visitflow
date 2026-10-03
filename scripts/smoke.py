#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Disposable MySQL acceptance run. Never run against production. Contact: www.zhuatech.cn."""
from pathlib import Path
import argparse, concurrent.futures, datetime as dt, http.cookiejar, json, os, secrets, threading, urllib.request, urllib.error, uuid
ROOT=Path(__file__).resolve().parents[1]
STATE=ROOT/'.smoke-state.json'
class Client:
    """Same-origin cookie + CSRF client; credentials never printed. 微信 zhuatech / zhuatech2。"""
    def __init__(self, base):
        self.base=base; self.jar=http.cookiejar.CookieJar(); self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar)); self.csrf=None
    def call(self,path,method='GET',body=None,expected=200):
        if self.csrf is None and path!='/auth/csrf': self.csrf=self.call('/auth/csrf')
        headers={'Content-Type':'application/json'}
        if method!='GET': headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(self.base+'/api'+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
        try:
            with self.opener.open(req,timeout=30) as res: status=res.status; value=json.load(res)
        except urllib.error.HTTPError as e: status=e.code; value=json.load(e)
        assert status==expected, f'{method} {path}: expected {expected}, got {status}, code={value.get("code") if isinstance(value,dict) else ""}'
        return value
    def login(self,name,password): return self.call('/auth/login','POST',{'username':name,'password':password})
def stamp(value): return value.isoformat().replace('+00:00','Z')
def key(): return str(uuid.uuid4())
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--base',default='http://127.0.0.1:8103');p.add_argument('--run',action='store_true');p.add_argument('--verify',action='store_true');args=p.parse_args()
    assert args.run != args.verify, 'Choose --run or --verify'
    from urllib.parse import urlparse
    parsed=urlparse(args.base);assert parsed.scheme=='http' and parsed.hostname=='127.0.0.1' and parsed.port, 'Use isolated loopback acceptance environment only'
    env=dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
    admin=Client(args.base);admin.login('admin',env['ADMIN_PASSWORD'])
    if args.verify:
        state=json.loads(STATE.read_text())
        finished=admin.call('/visits/'+str(state['finished']));assert finished['visit']['status']=='LEFT' and len(finished['events'])==5
        onsite=admin.call('/visits/'+str(state['onsite']));assert onsite['visit']['status']=='IN_SITE' and onsite['visit']['checkedOutAt'] is None
        assert any(b['inUse'] and b['badge']['id']==state['badge'] for b in admin.call('/badges'))
        assert admin.call('/visits/'+str(state['pending']))['visit']['status'] in {'PENDING','EXPIRED'}
        print('PASS: restart retained visit history, onsite facts, issued badge and pending confirmation');return
    assert not STATE.exists(), 'State exists; use fresh disposable database or verify'
    roles={r['name']:r['id'] for r in admin.call('/admin/roles')}
    department=admin.call('/admin/departments','POST',{'name':'访客接待验收测试部门'})['id']
    pw='Aa9'+secrets.token_urlsafe(24);accounts={}
    names=[('visit-test-requester','员工',department,'登记员工（验收测试）'),('visit-test-host','员工',department,'被访人（验收测试）'),('visit-test-desk','前台',department,'前台（验收测试）'),('visit-test-desk2','前台',department,'第二前台（验收测试）'),('visit-test-outsider','员工',1,'其他部门（验收测试）'),('visit-test-other-desk','前台',1,'其他部门前台（验收测试）')]
    clients={}
    for name,role,dept,display in names:
        accounts[name]=admin.call('/admin/users','POST',{'username':name,'displayName':display,'password':pw,'roleId':roles[role],'departmentId':dept,'enabled':True})['id']
        clients[name]=Client(args.base);clients[name].login(name,pw)
    employee=clients['visit-test-requester'];host=clients['visit-test-host'];desk=clients['visit-test-desk'];desk2=clients['visit-test-desk2'];outsider=clients['visit-test-outsider'];otherdesk=clients['visit-test-other-desk']
    now=dt.datetime.fromisoformat(employee.call('/options')['serverNow'].replace('Z','+00:00'));start=now+dt.timedelta(minutes=5);end=start+dt.timedelta(hours=1)
    def badge(code): return desk.call('/badges','POST',{'code':code,'name':'接待访客牌（验收测试）','departmentId':department,'enabled':True})
    free=badge('TEST-01');race=badge('TEST-02')
    def draft(title='来访人（验收测试）'):
        return employee.call('/visits','POST',{'visitorName':title,'organization':'隔离验收测试单位','purpose':'仅用于隔离验收测试的接待流程','hostId':accounts['visit-test-host'],'category':'BUSINESS','startsAt':stamp(start),'endsAt':stamp(end),'requestKey':key()})
    def payload(v,note='接待验收测试',b=free):return {'version':v['version'],'requestKey':key(),'note':note,'badgeId':b['id'],'identityConfirmed':True,'badgeReturned':True}
    def act(who,v,action,expected=200,c=None):return who.call(f'/visits/{v["id"]}/commands/{action}','POST',c or payload(v),expected)
    v=draft();v=act(employee,v,'submit');act(employee,v,'approve',403);v=act(host,v,'approve');
    outsider.call('/visits/'+str(v['id']),expected=403);outsider.call('/visits/'+str(v['id'])+'/report.json',expected=403);outsider.call('/admin/users',expected=403);otherdesk.call('/visits/'+str(v['id']),expected=403)
    assert all(x['id']!=v['id'] for x in outsider.call('/visits')['items'])
    c=payload(v);c['identityConfirmed']=False;act(desk,v,'check-in',400,c)
    c=payload(v);entered=act(desk,v,'check-in',c=c);replay=act(desk,v,'check-in',c=c);assert entered['id']==replay['id'] and entered['version']==replay['version']
    changed={**c,'note':'不同载荷'};act(desk,v,'check-in',409,changed)
    v=entered;act(employee,v,'cancel',409);c=payload(v);c['badgeReturned']=False;act(desk,v,'check-out',400,c)
    c=payload(v);c['version']=-1;act(desk,v,'check-out',409,c)
    v=act(desk,v,'check-out');assert v['status']=='LEFT';history=employee.call('/visits/'+str(v['id']));assert len(history['events'])==5
    raw=json.dumps(employee.call('/visits/'+str(v['id'])+'/report.json'));assert 'password' not in raw.lower() and 'zhuatech' not in raw.lower()
    desk.call('/badges/'+str(free['id'])+'?version='+str(free['version']),'DELETE',expected=409)
    def confirm(w):return act(host,act(employee,w,'submit'),'approve')
    a=confirm(draft('并发来访甲（验收测试）'));b=confirm(draft('并发来访乙（验收测试）'));gate=threading.Barrier(2)
    def compete(client,w):
        gate.wait()
        try:return act(client,w,'check-in',c=payload(w,b=race))
        except AssertionError as error:assert 'got 409' in str(error);return None
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        futures=[pool.submit(compete,desk,a),pool.submit(compete,desk2,b)];results=[f.result() for f in futures]
    assert sum(x is not None for x in results)==1;onsite=next(x for x in results if x is not None)
    desk.call('/badges/'+str(race['id']),'PUT',{**race,'enabled':False},409)
    assert any(x['id']==onsite['id'] for x in desk.call('/visits?onsite=true')['items'])
    pending=act(employee,draft('待确认来访（验收测试）'),'submit')
    assert any(x['id']==pending['id'] for x in host.call('/workbench')['reviews'])
    denied=confirm(draft('拒绝入场（验收测试）'));denied=act(desk,denied,'deny');assert denied['status']=='DENIED'
    revoked=confirm(draft('接待撤销（验收测试）'));revoked=act(host,revoked,'revoke');assert revoked['status']=='CANCELLED'
    assert desk.call('/dashboard')['onsite']==1
    state={'password':pw,'accounts':accounts,'department':department,'finished':v['id'],'onsite':onsite['id'],'badge':race['id'],'pending':pending['id'],'start':stamp(start),'end':stamp(end)}
    fd=os.open(STATE,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as out:json.dump(state,out)
    print('PASS: real MySQL reception, independent host, scoped privacy, manual verification, return confirmation, concurrent badge single winner, stale version, idempotency, denial, revocation and export')
if __name__=='__main__': main()
