import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'vite';

test('built demo API remains disabled with deliberately true demo/auth flags and local-auth mode', async () => {
  const oldAuth=process.env.VITE_ENABLE_AUTH_SUBMISSIONS, oldDemo=process.env.VITE_ENABLE_DEMO_WORKFLOWS, oldNode=process.env.NODE_ENV;
  const oldFetch=globalThis.fetch;
  try {
    process.env.VITE_ENABLE_AUTH_SUBMISSIONS='true';process.env.VITE_ENABLE_DEMO_WORKFLOWS='true';process.env.NODE_ENV='development';
    const built=await build({mode:'local-auth',logLevel:'silent',build:{write:false,minify:false,lib:{entry:'src/lib/demo-api.js',formats:['es'],fileName:'demo-guard'}}});
    const chunk=(Array.isArray(built)?built:[built]).flatMap(item=>item.output).find(item=>item.type==='chunk'&&item.isEntry);
    const compiled=await import('data:text/javascript;base64,'+Buffer.from(chunk.code).toString('base64'));
    assert.equal(compiled.DEMO_ENABLED,false);
    let requests=0;globalThis.fetch=()=>{requests++;throw Error('Unexpected request');};
    await assert.rejects(compiled.demoRequest('/api/demo/medicines','fictional-test-token'),/disabled/);
    assert.equal(requests,0);
  } finally {
    for(const [key,value] of Object.entries({VITE_ENABLE_AUTH_SUBMISSIONS:oldAuth,VITE_ENABLE_DEMO_WORKFLOWS:oldDemo,NODE_ENV:oldNode})) {
      if(value===undefined) delete process.env[key];else process.env[key]=value;
    }
    globalThis.fetch=oldFetch;
  }
});
