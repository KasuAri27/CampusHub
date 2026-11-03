/** 用整数分和百分之一小时做报价，避免 0.1+0.2 一类浮点误差影响金额显示。 */
export function tutoringTotal(price, hours) {
  function hundredths(value) {
    const text = String(value ?? '')
    if (!/^\d+(\.\d{1,2})?$/.test(text)) throw new TypeError('请输入最多两位小数的非负数')
    const [whole, fraction = ''] = text.split('.')
    return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'))
  }
  const cents = (hundredths(price) * hundredths(hours) + 50n) / 100n
  return `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`
}
