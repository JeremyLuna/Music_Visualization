// controlPanel.js
const STYLE_MAP = {
    'tab-container': {
        position: 'fixed',
        left: '0',
        top: '0',
        zIndex: '1000'
    },
    'tab': {
        position: 'absolute',
        left: '0',
        top: '0',
        width: '60px',
        height: '120px',
        background: 'linear-gradient(135deg, #ff6b6b, #ee5a24)',
        borderRadius: '0 0 15px 0',
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: 'white',
        fontWeight: 'bold',
        fontSize: '24px',
        textAlign: 'center',
        lineHeight: '1.2',
        opacity: '0.3',
        transition: 'all 0.3s ease',
        boxShadow: '2px 0 10px rgba(0,0,0,0.2)'
    },
    'tab-content': {
        position: 'fixed',
        left: '-350px',
        top: '0',
        width: '350px',
        height: '100vh',
        background: '#000',
        borderRadius: '0 0 20px 0',
        boxShadow: '5px 0 25px rgba(0,0,0,0.3)',
        transition: 'left 0.4s cubic-bezier(0.25, 0.8, 0.25, 1)',
        padding: '30px',
        zIndex: '999',
        overflowY: 'auto'
    },
    'close-btn': {
        position: 'absolute',
        top: '10px',
        right: '10px',
        background: 'transparent',
        border: 'none',
        fontSize: '24px',
        cursor: 'pointer',
        color: '#ccc'
    },
    'content': {
        padding: '15px',
        background: '#000',
        color: '#ccc'
    },
    'overlay': {
        position: 'fixed',
        top: '0',
        left: '0',
        width: '100vw',
        height: '100vh',
        background: 'rgba(0,0,0,0.5)',
        opacity: '0',
        visibility: 'hidden',
        transition: 'opacity 0.3s'
    }
};

export class ControlPanel {
    constructor(options = {}) {
        this.isOpen = false;
        this.init();
    }

    init() {
        this.createElements();
        this.attachEvents();
    }

    createElements() {
        // Overlay
        this.overlay = this.createEl('div', 'overlay', 'overlay');
        document.body.appendChild(this.overlay);

        // Tab container
        this.tabContainer = this.createEl('div', 'tab-container');
        document.body.appendChild(this.tabContainer);

        // Tab button
        this.tab = this.createEl('div', 'tab', 'tab', '⚙');
        this.tabContainer.appendChild(this.tab);

        // Tab content
        this.tabContent = this.createEl('div', 'tab-content', 'tabContent');
        this.tabContainer.appendChild(this.tabContent);

        // Close button
        this.closeBtn = this.createEl('button', 'close-btn', 'closeBtn');
        this.closeBtn.innerHTML = '&times;';
        this.tabContent.appendChild(this.closeBtn);

        // Heading and intro
        this.tabContent.appendChild(this.createTextEl('h2', 'Settings'));
        this.tabContent.appendChild(this.createTextEl('p', 'Create Audio Sources and add Signal Processors Here.'));
    }

    attachEvents() {
        this.tab.addEventListener('click', e => {
            e.stopPropagation();
            this.openPanel();
        });

        this.tab.addEventListener('mouseenter', () => {
            if (!this.isOpen) {
                this.tab.style.opacity = '1';
                this.tab.style.transform = 'translateX(5px)';
                this.tab.style.boxShadow = '4px 0 15px rgba(0,0,0,0.3)';
            }
        });
        this.tab.addEventListener('mouseleave', () => {
            if (!this.isOpen) {
                this.tab.style.opacity = '0.3';
                this.tab.style.transform = '';
                this.tab.style.boxShadow = '2px 0 10px rgba(0,0,0,0.2)';
            }
        });

        this.closeBtn.addEventListener('click', e => {
            e.stopPropagation();
            this.closePanel();
        });

        this.closeBtn.addEventListener('mouseenter', () => {
            this.closeBtn.style.color = '#fff';
            this.closeBtn.style.background = '#333';
        });
        this.closeBtn.addEventListener('mouseleave', () => {
            this.closeBtn.style.color = '#ccc';
            this.closeBtn.style.background = 'transparent';
        });

        this.overlay.addEventListener('click', () => this.closePanel());

        document.addEventListener('click', e => {
            if (this.isOpen && !this.tabContent.contains(e.target) && !this.tab.contains(e.target)) {
                this.closePanel();
            }
        });

        this.tabContent.addEventListener('click', e => e.stopPropagation());

        document.addEventListener('keydown', e => {
            if (e.key === 'Escape' && this.isOpen) {
                this.closePanel();
            }
        });
    }

    createDetails(summaryText) {
        const details = document.createElement('details');
        Object.assign(details.style, {
            marginBottom: '15px',
            border: '1px solid #444',
            borderRadius: '8px',
            overflow: 'hidden'
        });

        const summary = document.createElement('summary');
        summary.textContent = summaryText;
        Object.assign(summary.style, {
            background: '#222',
            padding: '15px',
            cursor: 'pointer',
            fontWeight: '600',
            color: '#fff',
            borderBottom: '1px solid #444'
        });
        details.appendChild(summary);

        const contentDiv = this.createEl('div', 'content');
        details.appendChild(contentDiv);

        details.addEventListener('toggle', () => {
            if (details.open) {
                summary.style.background = '#444';
            } else {
                summary.style.background = '#222';
            }
        });

        return [details, contentDiv];
    }

    createEl(tag, className = '', id = '', text = '') {
        const el = document.createElement(tag);
        if (className && STYLE_MAP[className]) {
            Object.assign(el.style, STYLE_MAP[className]);
        }
        if (id) el.id = id;
        if (text) el.textContent = text;
        return el;
    }

    createTextEl(tag, text) {
        const el = document.createElement(tag);
        el.textContent = text;
        if (tag === 'h2') {
            el.style.color = '#fff';
            el.style.marginBottom = '20px';
            el.style.fontSize = '24px';
        } else if (tag === 'p') {
            el.style.color = '#ccc';
            el.style.lineHeight = '1.6';
            el.style.marginBottom = '15px';
        }
        return el;
    }

    openPanel() {
        this.isOpen = true;
        this.tabContent.style.left = '0';
        this.overlay.style.opacity = '1';
        this.overlay.style.visibility = 'visible';
        this.tab.style.opacity = '1';
    }

    closePanel() {
        this.isOpen = false;
        this.tabContent.style.left = '-350px';
        this.overlay.style.opacity = '0';
        this.overlay.style.visibility = 'hidden';
        this.tab.style.opacity = '0.3';
    }
}
