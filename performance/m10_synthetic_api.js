// M10.C4: synthetic-only k6 workload; never pass a production endpoint.
import http from 'k6/http';
import { check, sleep } from 'k6';
export const options={vus:5,duration:'30s',thresholds:{http_req_failed:['rate<0.01'],http_req_duration:['p(95)<800','p(99)<1500']}};
const base=__ENV.M10_SYNTHETIC_URL||'';
if(__ENV.M10_SYNTHETIC_APPROVED!=='YES'||!/^http:\/\/127\.0\.0\.1:[0-9]+$/.test(base))
 throw new Error('Only approved local loopback synthetic backend is allowed');
export default function(){
 const response=http.get(base+'/api/reports/summary?from=2026-01-01&to=2026-01-31',{headers:{Authorization:'Bearer invalid-synthetic-token'}});
 check(response,{'anonymous/invalid access denied':r=>r.status===401||r.status===403});
 sleep(1);
}
