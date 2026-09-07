import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { describe, expect, it } from 'vitest'
import DashboardChart from './DashboardChart.vue'

const props = {
  data: [{ label: '1', fullLabel: '2026-08-01', value: 0 }, { label: '2', fullLabel: '2026-08-02', value: 3000.25 }],
  secondaryData: [{ label: '1', value: 0 }, { label: '2', value: 25.75 }],
  seriesLabel: 'Cash', secondaryLabel: 'Vouchers', color: '#0d9488', secondaryColor: '#f59e0b',
  money: true, ariaLabel: 'August 2026 payments',
}
describe('dashboard grouped columns', () => {
  it('exposes both exact series and full dates in accessible descriptions and the data table', async () => {
    const html = await renderToString(createSSRApp(DashboardChart, props))
    expect(html).toContain('2026-08-02: Cash USD 3,000.25; Vouchers USD 25.75')
    expect(html).toMatch(/<td[^>]*>USD 3,000\.25<\/td><td[^>]*>USD 25\.75<\/td>/)
    expect(html).toContain('View all chart data')
    expect(html).not.toContain('<path')
  })
  it('renders true zeros without fabricated minimum-height bars', async () => {
    const html = await renderToString(createSSRApp(DashboardChart, { ...props, data: [{ label: '1', value: 0 }], secondaryData: [{ label: '1', value: 0 }] }))
    expect(html).toContain('No activity in this period.')
    expect(html.match(/height="0" fill=/g)).toHaveLength(2)
  })
  it('keeps all 31 dates inspectable even when axis labels are reduced', async () => {
    const data = Array.from({ length: 31 }, (_, i) => ({ label: String(i + 1), value: i }))
    const html = await renderToString(createSSRApp(DashboardChart, { ...props, data, secondaryData: data, money: false }))
    expect(html.match(/class="column-group"/g)).toHaveLength(31)
    expect(html.match(/scope="row"/g)).toHaveLength(31)
  })
})
