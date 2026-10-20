const permanent = [1,2,3,4].flatMap(q=>Array.from({length:8},(_,i)=>q*10+i+1))
const primary = [5,6,7,8].flatMap(q=>Array.from({length:5},(_,i)=>q*10+i+1))

export function teethFor(dentition:'permanent'|'primary'|'mixed') {
 return dentition==='permanent'?permanent:dentition==='primary'?primary:[...permanent,...primary]
}
export function surfaceOptions(tooth:number) {
 const q=Math.floor(tooth/10),p=tooth%10
 return ['WHOLE','M','D','B','L','F',...(q===1||q===2||q===5||q===6?['P']:[]),p<=3?'I':'O']
}
