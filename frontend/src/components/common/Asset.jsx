import asset0 from '@/assets/figma/8-3-0.png';
import asset1 from '@/assets/figma/8-3-1.png';
import asset2 from '@/assets/figma/8-3-2.png';
import asset3 from '@/assets/figma/8-3-3.png';
import asset4 from '@/assets/figma/8-3-4.png';
import asset5 from '@/assets/figma/8-3-5.png';
import asset18 from '@/assets/figma/8-4-0.png';
import asset19 from '@/assets/figma/8-8-0.png';
import asset20 from '@/assets/figma/12-1798-0.png';
import asset25 from '@/assets/figma/12-2061-0.png';
import asset26 from '@/assets/figma/12-2061-1.png';
import asset27 from '@/assets/figma/32-767-0.png';
import asset32 from '@/assets/figma/18-3548-0.png';
import asset33 from '@/assets/figma/18-3548-1.png';
import asset34 from '@/assets/figma/18-3548-2.png';
import asset35 from '@/assets/figma/18-3548-3.png';
import asset36 from '@/assets/figma/18-3548-4.png';
import asset37 from '@/assets/figma/18-3548-5.png';
import asset38 from '@/assets/figma/18-3548-6.png';
import asset39 from '@/assets/figma/18-3548-7.png';
import asset40 from '@/assets/figma/18-3548-8.png';
import asset41 from '@/assets/figma/18-3548-35.svg';
import asset42 from '@/assets/figma/17-2-0.png';
import asset43 from '@/assets/figma/17-2-1.png';
import asset44 from '@/assets/figma/17-2-2.png';
import asset45 from '@/assets/figma/17-2-3.png';
import asset46 from '@/assets/figma/17-2-4.png';
import asset47 from '@/assets/figma/17-2-5.png';
import asset48 from '@/assets/figma/17-2-6.png';
import asset49 from '@/assets/figma/17-2-7.png';
import asset50 from '@/assets/figma/17-2-8.png';
import asset51 from '@/assets/figma/17-2-9.png';
import asset53 from '@/assets/figma/18-3548-36.svg';
import asset54 from '@/assets/figma/18-3548-37.svg';
import asset55 from '@/assets/figma/18-3548-38.svg';
import asset56 from '@/assets/figma/18-3548-39.svg';
import asset57 from '@/assets/figma/18-3548-40.svg';
import asset58 from '@/assets/figma/18-3548-41.svg';
import asset59 from '@/assets/figma/18-3548-42.svg';
import asset60 from '@/assets/figma/18-3548-43.svg';
import asset61 from '@/assets/figma/18-3548-44.svg';
import asset62 from '@/assets/figma/18-3548-45.svg';
import asset63 from '@/assets/figma/18-3548-46.svg';
import asset64 from '@/assets/figma/18-3548-47.svg';
import asset65 from '@/assets/figma/18-3548-48.svg';
import asset66 from '@/assets/figma/18-3548-49.svg';
import asset67 from '@/assets/figma/18-3548-50.svg';
import asset68 from '@/assets/figma/18-3548-51.svg';
import asset69 from '@/assets/figma/18-3548-52.svg';
import asset70 from '@/assets/figma/18-3548-53.svg';

const assets = {
  '8-3-0.png': asset0,
  '8-3-1.png': asset1,
  '8-3-2.png': asset2,
  '8-3-3.png': asset3,
  '8-3-4.png': asset4,
  '8-3-5.png': asset5,
  '8-4-0.png': asset18,
  '8-8-0.png': asset19,
  '12-1798-0.png': asset20,
  '12-2061-0.png': asset25,
  '12-2061-1.png': asset26,
  '32-767-0.png': asset27,
  '18-3548-0.png': asset32,
  '18-3548-1.png': asset33,
  '18-3548-2.png': asset34,
  '18-3548-3.png': asset35,
  '18-3548-4.png': asset36,
  '18-3548-5.png': asset37,
  '18-3548-6.png': asset38,
  '18-3548-7.png': asset39,
  '18-3548-8.png': asset40,
  '18-3548-35.svg': asset41,
  '17-2-0.png': asset42,
  '17-2-1.png': asset43,
  '17-2-2.png': asset44,
  '17-2-3.png': asset45,
  '17-2-4.png': asset46,
  '17-2-5.png': asset47,
  '17-2-6.png': asset48,
  '17-2-7.png': asset49,
  '17-2-8.png': asset50,
  '17-2-9.png': asset51,
  '18-3548-36.svg': asset53,
  '18-3548-37.svg': asset54,
  '18-3548-38.svg': asset55,
  '18-3548-39.svg': asset56,
  '18-3548-40.svg': asset57,
  '18-3548-41.svg': asset58,
  '18-3548-42.svg': asset59,
  '18-3548-43.svg': asset60,
  '18-3548-44.svg': asset61,
  '18-3548-45.svg': asset62,
  '18-3548-46.svg': asset63,
  '18-3548-47.svg': asset64,
  '18-3548-48.svg': asset65,
  '18-3548-49.svg': asset66,
  '18-3548-50.svg': asset67,
  '18-3548-51.svg': asset68,
  '18-3548-52.svg': asset69,
  '18-3548-53.svg': asset70,
};

export default function Asset({ name, alt = '', className = '', ...props }) {
  return <img src={assets[name]} alt={alt} className={className} {...props} />;
}
