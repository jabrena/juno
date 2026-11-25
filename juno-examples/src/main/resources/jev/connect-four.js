const board=document.getElementById('board'),msg=document.getElementById('msg'),cells=[];
const text={turn:'Your turn: pick a column',thinking:'Model is thinking...',lost:'The model wins',won:'You win!',draw:'Draw'};
let busy=false,state={board:'-'.repeat(42),status:'turn',last:-1};
for(let r=5;r>=0;r--)for(let c=0;c<7;c++){const b=document.createElement('button');b.className='cell';b.onclick=()=>play(c);board.append(b);cells[c*6+r]=b}
function draw(){const done=state.status!=='turn';cells.forEach((b,i)=>{const v=state.board[i];b.className='cell '+(v==='-'?'':v)+(i===state.last?' last':'');b.disabled=busy||done||state.board[Math.floor(i/6)*6+5]!=='-'});msg.textContent=busy?text.thinking:text[state.status]}
const wait=ms=>new Promise(r=>setTimeout(r,ms));
function request(path,body){return fetch(path,body===undefined?{cache:'no-store'}:{method:'POST',body:body}).then(r=>{if(!r.ok)throw new Error('HTTP '+r.status);return r.json()})}
async function call(path,body,applied){busy=true;draw();let error;for(let attempt=0;attempt<4;attempt++){try{if(attempt>0){await wait(700*attempt);if(applied){const s=await request('/api/state');if(applied(s)){state=s;busy=false;draw();return}}}state=await request(path,body);busy=false;draw();return}catch(e){error=e}}busy=false;draw();msg.textContent='Board busy ('+error.message+'), try again'}
const discs=s=>s.board.split('').filter(v=>v==='H').length;
function play(c){if(busy||state.status!=='turn')return;const before=discs(state);call('/api/move',''+c,s=>discs(s)>before)}
const reset=who=>call('/api/reset',who,s=>discs(s)===0&&s.status==='turn');
document.getElementById('you').onclick=()=>reset('h');document.getElementById('model').onclick=()=>reset('m');
call('/api/state');
