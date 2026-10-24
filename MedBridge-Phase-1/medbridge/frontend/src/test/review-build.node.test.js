import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'vite';

test('normal hosted builds cannot activate review from flags; explicit review bundles exclude local demo credentials',async()=>{
 const names=['VITE_ENABLE_REVIEW','VITE_ENABLE_AUTH_SUBMISSIONS','VITE_ENABLE_DEMO_WORKFLOWS','VITE_REVIEW_API_ORIGIN'];
 const prior=Object.fromEntries(names.map(k=>[k,process.env[k]]));
 try {
  process.env.VITE_ENABLE_REVIEW='true';process.env.VITE_ENABLE_AUTH_SUBMISSIONS='true';process.env.VITE_ENABLE_DEMO_WORKFLOWS='true';process.env.VITE_REVIEW_API_ORIGIN='https://fictional-review-api.example.invalid';
  const guard=await build({mode:'production',logLevel:'silent',build:{write:false,minify:false,lib:{entry:'src/lib/review-api.js',formats:['es'],fileName:'guard'}}});
  const chunk=(Array.isArray(guard)?guard:[guard]).flatMap(r=>r.output).find(r=>r.type==='chunk'&&r.isEntry);
  const compiled=await import('data:text/javascript;base64,'+Buffer.from(chunk.code).toString('base64'));
  assert.equal(compiled.REVIEW_ENABLED,false);
  for(const mode of ['production','review']){
   const result=await build({mode,logLevel:'silent',build:{write:false}});
   const code=(Array.isArray(result)?result:[result]).flatMap(r=>r.output).map(r=>r.type==='chunk'?r.code:String(r.source)).join('\n');
   for(const value of ['meera@medbridge.test','divya@medbridge.test','pending@medbridge.test','admin@medbridge.test','DemoDonor2026!','DemoNgo2026!','DemoPending2026!','DemoAdmin2026!'])assert.ok(!code.includes(value),'Local credential must not occur in '+mode+' assets');
  }
 }finally{for(const [key,value]of Object.entries(prior)){if(value===undefined)delete process.env[key];else process.env[key]=value;}}
});
