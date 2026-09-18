import React from 'react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  LineChart,
  Line,
  AreaChart,
  Area,
  RadarChart,
  Radar,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend
} from 'recharts';
import { Sparkles } from 'lucide-react';
import type { DynamicChartSpec } from '../../services/simulationTypes';

interface DynamicContextualChartProps {
  chartSpec?: DynamicChartSpec | null;
}

/**
 * Universal Declarative Chart Component
 * Renders dynamic charts synthesized by LLM from user document context + DES event logs.
 */
export const DynamicContextualChart: React.FC<DynamicContextualChartProps> = ({ chartSpec }) => {
  if (!chartSpec || !chartSpec.data || chartSpec.data.length === 0) {
    return null;
  }

  const renderChart = () => {
    switch (chartSpec.chartType) {
      case 'area':
        return (
          <AreaChart data={chartSpec.data}>
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
            <XAxis dataKey={chartSpec.xAxisKey} stroke="#94a3b8" tick={{ fontSize: 11 }} />
            <YAxis stroke="#94a3b8" tick={{ fontSize: 11 }} />
            <Tooltip
              contentStyle={{
                background: '#0f172a',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: '8px',
                fontSize: '12px'
              }}
            />
            <Legend wrapperStyle={{ fontSize: '12px' }} />
            {(chartSpec.yAxisKeys || []).map((y) => (
              <Area
                key={y.key}
                type="monotone"
                dataKey={y.key}
                name={y.name}
                stroke={y.color || '#818cf8'}
                fill={y.color || '#818cf8'}
                fillOpacity={0.35}
              />
            ))}
          </AreaChart>
        );

      case 'line':
        return (
          <LineChart data={chartSpec.data}>
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
            <XAxis dataKey={chartSpec.xAxisKey} stroke="#94a3b8" tick={{ fontSize: 11 }} />
            <YAxis stroke="#94a3b8" tick={{ fontSize: 11 }} />
            <Tooltip
              contentStyle={{
                background: '#0f172a',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: '8px',
                fontSize: '12px'
              }}
            />
            <Legend wrapperStyle={{ fontSize: '12px' }} />
            {(chartSpec.yAxisKeys || []).map((y) => (
              <Line
                key={y.key}
                type="monotone"
                dataKey={y.key}
                name={y.name}
                stroke={y.color || '#38bdf8'}
                strokeWidth={2}
                dot={{ r: 3 }}
              />
            ))}
          </LineChart>
        );

      case 'radar':
        return (
          <RadarChart data={chartSpec.data}>
            <PolarGrid stroke="rgba(255,255,255,0.1)" />
            <PolarAngleAxis dataKey={chartSpec.xAxisKey} stroke="#94a3b8" tick={{ fontSize: 11 }} />
            <PolarRadiusAxis stroke="#64748b" tick={{ fontSize: 10 }} />
            <Tooltip
              contentStyle={{
                background: '#0f172a',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: '8px',
                fontSize: '12px'
              }}
            />
            <Legend wrapperStyle={{ fontSize: '12px' }} />
            {(chartSpec.yAxisKeys || []).map((y) => (
              <Radar
                key={y.key}
                name={y.name}
                dataKey={y.key}
                stroke={y.color || '#c084fc'}
                fill={y.color || '#c084fc'}
                fillOpacity={0.4}
              />
            ))}
          </RadarChart>
        );

      case 'bar':
      default:
        return (
          <BarChart data={chartSpec.data}>
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
            <XAxis dataKey={chartSpec.xAxisKey} stroke="#94a3b8" tick={{ fontSize: 11 }} />
            <YAxis stroke="#94a3b8" tick={{ fontSize: 11 }} />
            <Tooltip
              contentStyle={{
                background: '#0f172a',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: '8px',
                fontSize: '12px'
              }}
            />
            <Legend wrapperStyle={{ fontSize: '12px' }} />
            {(chartSpec.yAxisKeys || []).map((y) => (
              <Bar
                key={y.key}
                dataKey={y.key}
                name={y.name}
                fill={y.color || '#818cf8'}
                stackId={y.stackId}
                radius={[4, 4, 0, 0]}
              />
            ))}
          </BarChart>
        );
    }
  };

  return (
    <div
      style={{
        background: '#111827',
        borderRadius: '12px',
        border: '1px solid rgba(255,255,255,0.08)',
        padding: '16px',
        marginTop: '16px',
        boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
        width: '100%',
        minWidth: 0,
        boxSizing: 'border-box'
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Sparkles size={16} color="#818cf8" />
          <h4 style={{ margin: 0, color: '#f8fafc', fontSize: '0.92rem', fontWeight: 600 }}>
            {chartSpec.chartTitle || 'Context-Driven Process Analytics'}
          </h4>
        </div>
        <span
          style={{
            fontSize: '0.68rem',
            background: 'rgba(129,140,248,0.15)',
            color: '#818cf8',
            padding: '2px 8px',
            borderRadius: '12px',
            textTransform: 'uppercase',
            letterSpacing: '0.5px'
          }}
        >
          AI Contextual Graph
        </span>
      </div>

      {chartSpec.chartSubtitle && (
        <p style={{ margin: '0 0 14px 0', color: '#94a3b8', fontSize: '0.76rem', lineHeight: '1.4' }}>
          {chartSpec.chartSubtitle}
        </p>
      )}

      <div style={{ height: '260px', width: '100%', minWidth: 0 }}>
        <ResponsiveContainer width="100%" height={260}>
          {renderChart()}
        </ResponsiveContainer>
      </div>

      {chartSpec.managerialInsight && (
        <div
          style={{
            marginTop: '14px',
            padding: '10px 12px',
            background: 'rgba(99,102,241,0.08)',
            borderRadius: '8px',
            borderLeft: '3px solid #818cf8',
            fontSize: '0.78rem',
            color: '#e0e7ff',
            lineHeight: '1.5'
          }}
        >
          <strong style={{ color: '#a5b4fc' }}>💡 Managerial Takeaway:</strong> {chartSpec.managerialInsight}
        </div>
      )}
    </div>
  );
};
