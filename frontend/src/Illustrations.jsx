/* Small, friendly line-art. Navy outlines, soft fills, rounded joins. Decorative: aria-hidden. */

const STROKE = { stroke: '#0F172A', strokeWidth: 2.5, strokeLinejoin: 'round', strokeLinecap: 'round' };

export function WarehouseIcon({ size = 64, fill = '#DBEAFE' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
      <path d="M8 26 L32 10 L56 26 V54 H8 Z" fill={fill} {...STROKE} />
      <rect x="22" y="34" width="20" height="20" rx="2" fill="#FFFFFF" {...STROKE} />
      <path d="M22 41 H42 M22 47 H42" {...STROKE} strokeWidth="2" />
      <circle cx="32" cy="23" r="3.5" fill="#FFFFFF" {...STROKE} strokeWidth="2" />
    </svg>
  );
}

export function BoxIcon({ size = 40, fill = '#FDE68A' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" aria-hidden="true">
      <path d="M5 13 L20 6 L35 13 V30 L20 36 L5 30 Z" fill={fill} {...STROKE} />
      <path d="M5 13 L20 20 L35 13 M20 20 V36" {...STROKE} fill="none" />
      <path d="M12.5 9.5 L27.5 16.5" {...STROKE} strokeWidth="2" fill="none" />
    </svg>
  );
}

export function HouseIcon({ size = 64 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
      <path d="M10 30 L32 12 L54 30" fill="none" {...STROKE} />
      <path d="M15 27 V54 H49 V27" fill="#ECFDF5" {...STROKE} />
      <rect x="27" y="38" width="10" height="16" rx="2" fill="#FFFFFF" {...STROKE} />
      <rect x="40" y="33" width="6" height="6" rx="1" fill="#FFFFFF" {...STROKE} strokeWidth="2" />
      <path d="M42 12 V20" {...STROKE} />
    </svg>
  );
}

export function TruckIcon({ size = 56 }) {
  return (
    <svg width={size} height={size * 0.64} viewBox="0 0 56 36" aria-hidden="true">
      <rect x="3" y="6" width="30" height="20" rx="3" fill="#FFFFFF" {...STROKE} />
      <path d="M33 12 H44 L52 20 V26 H33 Z" fill="#DBEAFE" {...STROKE} />
      <circle cx="14" cy="28" r="4.5" fill="#FFFFFF" {...STROKE} />
      <circle cx="43" cy="28" r="4.5" fill="#FFFFFF" {...STROKE} />
    </svg>
  );
}

/** Hero scene: two warehouses send boxes along dashed roads to a house. */
export function HeroScene() {
  return (
    <svg className="hero-scene" viewBox="0 0 420 220" role="img"
         aria-label="Two warehouses, Montréal and Ottawa, sending boxes to one house">
      <path d="M92 70 C180 70 220 110 318 118" fill="none" stroke="#94A3B8" strokeWidth="3" strokeDasharray="2 9" strokeLinecap="round" />
      <path d="M92 168 C180 168 220 130 318 124" fill="none" stroke="#94A3B8" strokeWidth="3" strokeDasharray="2 9" strokeLinecap="round" />
      <g transform="translate(20 30)"><WarehouseIcon size={78} /></g>
      <g transform="translate(20 128)"><WarehouseIcon size={78} fill="#E0E7FF" /></g>
      <text x="59" y="122" textAnchor="middle" className="scene-label">Montréal</text>
      <text x="59" y="220" textAnchor="middle" className="scene-label">Ottawa</text>
      <g transform="translate(168 62)"><g className="float-a"><BoxIcon size={38} /></g></g>
      <g transform="translate(196 142)"><g className="float-b"><BoxIcon size={38} fill="#FBCFE8" /></g></g>
      <g transform="translate(318 78)"><HouseIcon size={90} /></g>
      <text x="363" y="186" textAnchor="middle" className="scene-label">You</text>
    </svg>
  );
}

export function Sparkle({ size = 22 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 2 L14 10 L22 12 L14 14 L12 22 L10 14 L2 12 L10 10 Z" fill="#FDE68A" {...STROKE} strokeWidth="2" />
    </svg>
  );
}
