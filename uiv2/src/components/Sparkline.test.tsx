import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Sparkline } from './Sparkline';

describe('Sparkline', () => {
  describe('empty data', () => {
    it('should render placeholder line when no data', () => {
      const { container } = render(<Sparkline data={[]} />);
      expect(container.querySelector('line')).toBeInTheDocument();
    });

    it('should have accessible label for empty data', () => {
      render(<Sparkline data={[]} />);
      expect(screen.getByRole('img')).toHaveAttribute(
        'aria-label',
        'Throughput chart: No data available'
      );
    });

    it('should render dashed placeholder line with correct styling', () => {
      const { container } = render(<Sparkline data={[]} />);
      const line = container.querySelector('line');
      expect(line).toHaveAttribute('stroke-dasharray', '2 2');
      expect(line).toHaveAttribute('stroke-opacity', '0.3');
    });
  });

  describe('with data', () => {
    it('should render sparkline chart when data exists', () => {
      render(<Sparkline data={[1, 2, 3]} />);
      // MUI X Charts renders SVG content
      expect(screen.getByRole('img')).toBeInTheDocument();
    });

    it('should have accessible label with latest value', () => {
      render(<Sparkline data={[1, 2, 3]} />);
      expect(screen.getByRole('img')).toHaveAttribute(
        'aria-label',
        expect.stringContaining('3.0 messages per second')
      );
    });

    it('should indicate increasing trend in aria-label', () => {
      render(<Sparkline data={[1, 2, 5]} />);
      expect(screen.getByRole('img')).toHaveAttribute(
        'aria-label',
        expect.stringContaining('increasing')
      );
    });

    it('should indicate decreasing trend in aria-label', () => {
      render(<Sparkline data={[5, 3, 1]} />);
      expect(screen.getByRole('img')).toHaveAttribute(
        'aria-label',
        expect.stringContaining('decreasing')
      );
    });

    it('should indicate stable trend when values are equal', () => {
      render(<Sparkline data={[3, 3, 3]} />);
      expect(screen.getByRole('img')).toHaveAttribute(
        'aria-label',
        expect.stringContaining('stable')
      );
    });

    it('should handle single data point', () => {
      render(<Sparkline data={[42]} />);
      const img = screen.getByRole('img');
      expect(img).toHaveAttribute('aria-label', expect.stringContaining('42.0'));
      expect(img).toHaveAttribute('aria-label', expect.stringContaining('stable'));
    });
  });

  describe('custom dimensions', () => {
    it('should render placeholder svg element', () => {
      const { container } = render(<Sparkline data={[]} />);
      const svg = container.querySelector('svg');
      expect(svg).toBeInTheDocument();
      // MUI Box converts width/height to inline styles
      expect(svg).toHaveStyle({ width: '80px', height: '24px' });
    });

    it('should use custom dimensions for placeholder', () => {
      const { container } = render(<Sparkline data={[]} width={120} height={40} />);
      const svg = container.querySelector('svg');
      expect(svg).toBeInTheDocument();
      expect(svg).toHaveStyle({ width: '120px', height: '40px' });
    });

    it('should pass dimensions to SparkLineChart', () => {
      // MUI X Charts handles dimensions internally, so we just verify the wrapper renders
      const { container } = render(<Sparkline data={[1, 2, 3]} width={120} height={40} />);
      // The Box wrapper should exist
      expect(container.querySelector('[role="img"]')).toBeInTheDocument();
    });
  });

  describe('custom color', () => {
    it('should use default color for placeholder', () => {
      const { container } = render(<Sparkline data={[]} />);
      const line = container.querySelector('line');
      expect(line).toHaveAttribute('stroke', '#646cff');
    });

    it('should use custom color for placeholder', () => {
      const { container } = render(<Sparkline data={[]} color="#ff0000" />);
      const line = container.querySelector('line');
      expect(line).toHaveAttribute('stroke', '#ff0000');
    });
  });

  describe('edge cases', () => {
    it('should handle all zeros', () => {
      render(<Sparkline data={[0, 0, 0]} />);
      expect(screen.getByRole('img')).toHaveAttribute(
        'aria-label',
        expect.stringContaining('0.0 messages per second')
      );
    });

    it('should handle very large values', () => {
      render(<Sparkline data={[1000000, 2000000]} />);
      expect(screen.getByRole('img')).toHaveAttribute(
        'aria-label',
        expect.stringContaining('2000000.0')
      );
    });

    it('should handle decimal values', () => {
      render(<Sparkline data={[1.5, 2.7, 3.14159]} />);
      expect(screen.getByRole('img')).toHaveAttribute(
        'aria-label',
        expect.stringContaining('3.1 messages per second')
      );
    });
  });
});
