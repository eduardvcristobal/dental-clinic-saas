/** Patient-safe display labels: provider acceptance is not proof of delivery. */
export function deliveryStatusLabel(status:string):string {
 switch(status){
  case 'PENDING': return 'Queued — not sent'
  case 'CLAIMED': return 'Processing — delivery unconfirmed'
  case 'ACCEPTED': return 'Accepted by provider — delivery unconfirmed'
  case 'DELIVERED': return 'Delivered (provider reported)'
  case 'FAILED': return 'Retry pending — not delivered'
  case 'SUPPRESSED': return 'Not sending'
  default: return 'Delivery status unknown'
 }
}
